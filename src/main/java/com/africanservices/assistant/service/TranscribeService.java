package com.africanservices.assistant.service;

import com.africanservices.assistant.util.StructuredLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Transcribe client wrapper service for speech-to-text operations.
 * Provides standard (non-real-time) transcription functionality.
 * 
 * Validates Requirements: 2.3, 2.4, 2.5, 9.3, 11.6
 */
@Service
public class TranscribeService {

    private final TranscribeClient transcribeClient;
    private final S3Service s3Service;
    private final StructuredLogger logger;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String audioBucketName;
    private final long pollingIntervalMs;
    private final int maxPollingAttempts;

    @Autowired
    public TranscribeService(
            TranscribeClient transcribeClient, 
            S3Service s3Service, 
            StructuredLogger logger,
            @Value("${aws.s3.audio-bucket:language-assistant-audio-dev}") String audioBucketName,
            @Value("${aws.transcribe.polling-interval-ms:2000}") long pollingIntervalMs,
            @Value("${aws.transcribe.max-polling-attempts:30}") int maxPollingAttempts) {
        this.transcribeClient = transcribeClient;
        this.s3Service = s3Service;
        this.logger = logger;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.audioBucketName = audioBucketName;
        this.pollingIntervalMs = pollingIntervalMs;
        this.maxPollingAttempts = maxPollingAttempts;
    }

    /**
     * Submit audio file for transcription.
     * Uses standard transcription mode (not real-time streaming).
     * 
     * @param fileId S3 key of the audio file
     * @param audioFormat Audio format (wav, mp3, m4a)
     * @param languageCode Language code for transcription (e.g., "en-US", "ha-NG")
     * @return Transcription job name if successful, null otherwise
     */
    public String submitTranscriptionJob(String fileId, String audioFormat, String languageCode) {
        try {
            String jobName = generateJobName(fileId);
            String s3Uri = String.format("s3://%s/%s", audioBucketName, fileId);
            
            Media media = Media.builder()
                    .mediaFileUri(s3Uri)
                    .build();

            StartTranscriptionJobRequest request = StartTranscriptionJobRequest.builder()
                    .transcriptionJobName(jobName)
                    .media(media)
                    .mediaFormat(mapAudioFormat(audioFormat))
                    .languageCode(languageCode)
                    .build();

            transcribeClient.startTranscriptionJob(request);
            
            logger.info("Transcription job submitted: jobName={}, fileId={}, languageCode={}", 
                    jobName, fileId, languageCode);
            
            return jobName;
        } catch (TranscribeException e) {
            logger.warn("Failed to submit transcription job: fileId={}, error={}", 
                    fileId, e.getMessage());
            return null;
        }
    }

    /**
     * Poll for transcription completion and retrieve result.
     * Polls at configured intervals until job completes or max attempts reached.
     * 
     * @param jobName Transcription job name
     * @return Transcribed text if successful, null otherwise
     */
    public String getTranscriptionResult(String jobName) {
        try {
            for (int attempt = 0; attempt < maxPollingAttempts; attempt++) {
                GetTranscriptionJobRequest request = GetTranscriptionJobRequest.builder()
                        .transcriptionJobName(jobName)
                        .build();

                GetTranscriptionJobResponse response = transcribeClient.getTranscriptionJob(request);
                TranscriptionJob job = response.transcriptionJob();
                TranscriptionJobStatus status = job.transcriptionJobStatus();

                logger.debug("Polling transcription job: jobName={}, status={}, attempt={}", 
                        jobName, status.toString(), attempt + 1);

                if (status == TranscriptionJobStatus.COMPLETED) {
                    String transcriptUri = job.transcript().transcriptFileUri();
                    String transcribedText = retrieveTranscriptFromUri(transcriptUri);
                    
                    logger.info("Transcription completed: jobName={}, textLength={}", 
                            jobName, transcribedText != null ? transcribedText.length() : 0);
                    
                    return transcribedText;
                } else if (status == TranscriptionJobStatus.FAILED) {
                    logger.warn("Transcription job failed: jobName={}, failureReason={}", 
                            jobName, job.failureReason());
                    return null;
                }

                // Wait before next poll
                Thread.sleep(pollingIntervalMs);
            }

            logger.warn("Transcription job timed out: jobName={}, maxAttempts={}", 
                    jobName, maxPollingAttempts);
            return null;
        } catch (TranscribeException e) {
            logger.warn("Failed to retrieve transcription result: jobName={}, error={}", 
                    jobName, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Transcription polling interrupted: jobName={}", jobName);
            return null;
        }
    }

    /**
     * Submit transcription job and wait for result.
     * Combines submission and polling into a single operation.
     * Deletes temporary audio file after retrieval.
     * 
     * @param fileId S3 key of the audio file
     * @param audioFormat Audio format (wav, mp3, m4a)
     * @param languageCode Language code for transcription
     * @return Transcribed text if successful, null otherwise
     */
    public String transcribeAudio(String fileId, String audioFormat, String languageCode) {
        try {
            // Submit transcription job
            String jobName = submitTranscriptionJob(fileId, audioFormat, languageCode);
            if (jobName == null) {
                return null;
            }

            // Poll for result
            String transcribedText = getTranscriptionResult(jobName);
            
            // Delete temporary audio file after retrieval
            if (transcribedText != null) {
                boolean deleted = s3Service.deleteAudioFile(fileId);
                if (!deleted) {
                    logger.warn("Failed to delete temporary audio file: fileId={}", fileId);
                }
            }

            return transcribedText;
        } catch (Exception e) {
            logger.warn("Transcription failed: fileId={}, error={}", fileId, e.getMessage());
            return null;
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Generate unique transcription job name from file ID.
     * 
     * @param fileId S3 key of the audio file
     * @return Job name
     */
    private String generateJobName(String fileId) {
        // Replace invalid characters for job name
        String sanitized = fileId.replaceAll("[^a-zA-Z0-9._-]", "_");
        long timestamp = System.currentTimeMillis();
        return String.format("transcribe_%d_%s", timestamp, sanitized);
    }

    /**
     * Map audio format to Transcribe MediaFormat enum.
     * 
     * @param audioFormat Audio format string (wav, mp3, m4a)
     * @return MediaFormat enum value
     */
    private MediaFormat mapAudioFormat(String audioFormat) {
        return switch (audioFormat.toLowerCase()) {
            case "wav" -> MediaFormat.WAV;
            case "mp3" -> MediaFormat.MP3;
            case "m4a" -> MediaFormat.MP4;
            default -> MediaFormat.MP3; // Default fallback
        };
    }

    /**
     * Retrieve transcript text from Transcribe result URI.
     * The URI points to a JSON file containing the transcript.
     * 
     * @param transcriptUri URI of the transcript JSON file
     * @return Transcribed text
     */
    private String retrieveTranscriptFromUri(String transcriptUri) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(transcriptUri))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                logger.warn("Failed to retrieve transcript: statusCode={}, uri={}", 
                        response.statusCode(), transcriptUri);
                return null;
            }

            // Parse JSON response to extract transcript text
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode transcripts = root.path("results").path("transcripts");
            
            if (transcripts.isArray() && transcripts.size() > 0) {
                return transcripts.get(0).path("transcript").asText();
            }

            logger.warn("No transcript found in response: uri={}", transcriptUri);
            return null;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to retrieve transcript from URI: uri={}, error={}", 
                    transcriptUri, e.getMessage());
            return null;
        }
    }
}

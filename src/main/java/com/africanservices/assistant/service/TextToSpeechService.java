package com.africanservices.assistant.service;

import com.africanservices.assistant.util.MetricsEmitter;
import com.africanservices.assistant.util.StructuredLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Text-to-Speech service that converts text responses to speech.
 * Integrates Polly for speech synthesis and S3 for audio storage.
 * Implements graceful degradation for Polly failures.
 * 
 * Validates Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 9.2, 11.5
 */
@Service
public class TextToSpeechService {

    private static final StructuredLogger logger = new StructuredLogger(TextToSpeechService.class);
    
    private final PollyService pollyService;
    private final S3Service s3Service;
    private final MetricsEmitter metricsEmitter;

    @Autowired
    public TextToSpeechService(
            PollyService pollyService,
            S3Service s3Service,
            MetricsEmitter metricsEmitter) {
        this.pollyService = pollyService;
        this.s3Service = s3Service;
        this.metricsEmitter = metricsEmitter;
    }

    /**
     * Convert text to speech and return audio URL.
     * Implements graceful degradation - returns null if TTS fails.
     * 
     * @param text Text to convert to speech
     * @param language Language code (ha, yo, ig, ff, en)
     * @param userId User identifier for organizing files
     * @return TTS result containing audio URL and expiration, or null if TTS fails
     */
    public TTSResult convertTextToSpeech(String text, String language, String userId) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.debug("Converting text to speech: language={}, textLength={}", 
                        language, text.length());
            
            // Step 1: Synthesize speech using Polly
            byte[] audioData = pollyService.synthesizeSpeech(text, language);
            
            if (audioData == null) {
                logger.warn("Polly synthesis failed, implementing graceful degradation");
                metricsEmitter.emitMetric("TTSFailure", 1.0, 
                        software.amazon.awssdk.services.cloudwatch.model.StandardUnit.COUNT, 
                        language, null);
                return null; // Graceful degradation - return null for text-only response
            }
            
            // Step 2: Store audio file in S3
            String fileId = s3Service.uploadAudioFile(audioData, "mp3", userId);
            
            if (fileId == null) {
                logger.warn("S3 upload failed, implementing graceful degradation");
                metricsEmitter.emitMetric("TTSFailure", 1.0, 
                        software.amazon.awssdk.services.cloudwatch.model.StandardUnit.COUNT, 
                        language, null);
                return null; // Graceful degradation
            }
            
            // Step 3: Generate presigned URL with 1-hour expiration
            String audioUrl = s3Service.generatePresignedUrl(fileId);
            
            if (audioUrl == null) {
                logger.warn("Presigned URL generation failed, cleaning up S3 file");
                s3Service.deleteAudioFile(fileId);
                metricsEmitter.emitMetric("TTSFailure", 1.0, 
                        software.amazon.awssdk.services.cloudwatch.model.StandardUnit.COUNT, 
                        language, null);
                return null; // Graceful degradation
            }
            
            // Calculate expiration timestamp (1 hour from now)
            Instant expiration = Instant.now().plus(s3Service.getPresignedUrlExpiration());
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Successfully converted text to speech: language={}, duration={}ms", 
                       language, duration);
            
            metricsEmitter.emitMetric("TTSSuccess", 1.0, 
                    software.amazon.awssdk.services.cloudwatch.model.StandardUnit.COUNT, 
                    language, null);
            metricsEmitter.emitMetric("TTSLatency", duration, 
                    software.amazon.awssdk.services.cloudwatch.model.StandardUnit.MILLISECONDS, 
                    language, null);
            
            return new TTSResult(audioUrl, expiration);
            
        } catch (Exception e) {
            logger.warn("Unexpected error during TTS conversion: {}", e.getMessage());
            metricsEmitter.emitMetric("TTSFailure", 1.0, 
                    software.amazon.awssdk.services.cloudwatch.model.StandardUnit.COUNT, 
                    language, null);
            return null; // Graceful degradation
        }
    }

    /**
     * Result object containing audio URL and expiration timestamp.
     */
    public static class TTSResult {
        private final String audioUrl;
        private final Instant expiration;

        public TTSResult(String audioUrl, Instant expiration) {
            this.audioUrl = audioUrl;
            this.expiration = expiration;
        }

        public String getAudioUrl() {
            return audioUrl;
        }

        public Instant getExpiration() {
            return expiration;
        }

        public long getExpirationEpochSeconds() {
            return expiration.getEpochSecond();
        }
    }
}

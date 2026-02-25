package com.africanservices.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * S3 client wrapper service for audio file operations.
 * Provides upload, presigned URL generation, and deletion operations.
 * 
 * Validates Requirements: 2.2, 5.3, 5.4, 13.3, 13.4
 */
@Service
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofHours(1);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.audio-bucket:language-assistant-audio-dev}")
    private String audioBucketName;

    @Autowired
    public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * Upload audio file to S3 with a unique identifier.
     * 
     * @param audioData Audio file content as byte array
     * @param audioFormat Audio format (e.g., "wav", "mp3", "m4a")
     * @param userId User identifier for organizing files
     * @return Unique file identifier (S3 key) if successful, null otherwise
     */
    public String uploadAudioFile(byte[] audioData, String audioFormat, String userId) {
        try {
            // Generate unique file identifier
            String fileId = generateUniqueFileId(userId, audioFormat);
            
            // Determine content type based on format
            String contentType = getContentType(audioFormat);
            
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(audioBucketName)
                    .key(fileId)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(audioData));
            
            logger.info("Uploaded audio file: fileId={}, size={} bytes, format={}", 
                       fileId, audioData.length, audioFormat);
            return fileId;
        } catch (S3Exception e) {
            logger.error("Failed to upload audio file: userId={}, format={}", userId, audioFormat, e);
            return null;
        }
    }

    /**
     * Generate presigned URL for audio file with 1-hour expiration.
     * 
     * @param fileId S3 key of the audio file
     * @return Presigned URL string if successful, null otherwise
     */
    public String generatePresignedUrl(String fileId) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(audioBucketName)
                    .key(fileId)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(PRESIGNED_URL_EXPIRATION)
                    .getObjectRequest(getRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();
            
            logger.debug("Generated presigned URL: fileId={}, expiration={}h", 
                        fileId, PRESIGNED_URL_EXPIRATION.toHours());
            return url;
        } catch (S3Exception e) {
            logger.error("Failed to generate presigned URL: fileId={}", fileId, e);
            return null;
        }
    }

    /**
     * Delete audio file from S3.
     * 
     * @param fileId S3 key of the audio file
     * @return true if file was deleted successfully, false otherwise
     */
    public boolean deleteAudioFile(String fileId) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(audioBucketName)
                    .key(fileId)
                    .build();

            s3Client.deleteObject(deleteRequest);
            
            logger.info("Deleted audio file: fileId={}", fileId);
            return true;
        } catch (S3Exception e) {
            logger.error("Failed to delete audio file: fileId={}", fileId, e);
            return false;
        }
    }

    /**
     * Check if audio file exists in S3.
     * 
     * @param fileId S3 key of the audio file
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(String fileId) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(audioBucketName)
                    .key(fileId)
                    .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            logger.debug("File does not exist: fileId={}", fileId);
            return false;
        } catch (S3Exception e) {
            logger.error("Failed to check file existence: fileId={}", fileId, e);
            return false;
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Generate unique file identifier with UUID.
     * Format: audio/{userId}/{timestamp}-{uuid}.{format}
     * 
     * @param userId User identifier
     * @param audioFormat Audio format extension
     * @return Unique S3 key
     */
    private String generateUniqueFileId(String userId, String audioFormat) {
        String uuid = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        return String.format("audio/%s/%d-%s.%s", userId, timestamp, uuid, audioFormat.toLowerCase());
    }

    /**
     * Get MIME content type for audio format.
     * 
     * @param audioFormat Audio format (wav, mp3, m4a)
     * @return MIME content type
     */
    private String getContentType(String audioFormat) {
        return switch (audioFormat.toLowerCase()) {
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            default -> "application/octet-stream";
        };
    }

    /**
     * Get presigned URL expiration duration.
     * 
     * @return Duration of 1 hour
     */
    public Duration getPresignedUrlExpiration() {
        return PRESIGNED_URL_EXPIRATION;
    }
}

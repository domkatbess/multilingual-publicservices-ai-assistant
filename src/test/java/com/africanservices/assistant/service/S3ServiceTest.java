package com.africanservices.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for S3Service.
 */
@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3Service s3Service;

    private static final String TEST_BUCKET = "test-audio-bucket";
    private static final String TEST_USER_ID = "user123";
    private static final byte[] TEST_AUDIO_DATA = "test audio content".getBytes();

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Client, s3Presigner);
        // Set bucket name via reflection to avoid Spring context
        try {
            var field = S3Service.class.getDeclaredField("audioBucketName");
            field.setAccessible(true);
            field.set(s3Service, TEST_BUCKET);
        } catch (Exception e) {
            fail("Failed to set test bucket name: " + e.getMessage());
        }
    }

    @Test
    void testUploadAudioFile_Success() {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Act
        String fileId = s3Service.uploadAudioFile(TEST_AUDIO_DATA, "mp3", TEST_USER_ID);

        // Assert
        assertNotNull(fileId, "File ID should not be null");
        assertTrue(fileId.startsWith("audio/" + TEST_USER_ID + "/"), 
                  "File ID should start with audio/{userId}/");
        assertTrue(fileId.endsWith(".mp3"), "File ID should end with .mp3");
        assertTrue(fileId.contains("-"), "File ID should contain UUID separator");

        // Verify S3 client was called
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        
        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TEST_BUCKET, capturedRequest.bucket());
        assertEquals("audio/mpeg", capturedRequest.contentType());
    }

    @Test
    void testUploadAudioFile_WavFormat() {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Act
        String fileId = s3Service.uploadAudioFile(TEST_AUDIO_DATA, "wav", TEST_USER_ID);

        // Assert
        assertNotNull(fileId);
        assertTrue(fileId.endsWith(".wav"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals("audio/wav", requestCaptor.getValue().contentType());
    }

    @Test
    void testUploadAudioFile_M4aFormat() {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Act
        String fileId = s3Service.uploadAudioFile(TEST_AUDIO_DATA, "m4a", TEST_USER_ID);

        // Assert
        assertNotNull(fileId);
        assertTrue(fileId.endsWith(".m4a"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals("audio/mp4", requestCaptor.getValue().contentType());
    }

    @Test
    void testUploadAudioFile_UniqueIdentifiers() {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Act - upload multiple files
        String fileId1 = s3Service.uploadAudioFile(TEST_AUDIO_DATA, "mp3", TEST_USER_ID);
        String fileId2 = s3Service.uploadAudioFile(TEST_AUDIO_DATA, "mp3", TEST_USER_ID);
        String fileId3 = s3Service.uploadAudioFile(TEST_AUDIO_DATA, "mp3", TEST_USER_ID);

        // Assert - all file IDs should be unique
        assertNotNull(fileId1);
        assertNotNull(fileId2);
        assertNotNull(fileId3);
        assertNotEquals(fileId1, fileId2, "File IDs should be unique");
        assertNotEquals(fileId2, fileId3, "File IDs should be unique");
        assertNotEquals(fileId1, fileId3, "File IDs should be unique");
    }

    @Test
    void testUploadAudioFile_S3Exception() {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());

        // Act
        String fileId = s3Service.uploadAudioFile(TEST_AUDIO_DATA, "mp3", TEST_USER_ID);

        // Assert
        assertNull(fileId, "File ID should be null on S3 exception");
    }

    @Test
    void testGeneratePresignedUrl_Success() throws Exception {
        // Arrange
        String testFileId = "audio/user123/12345-uuid.mp3";
        String expectedUrl = "https://test-bucket.s3.amazonaws.com/audio/user123/12345-uuid.mp3?signature=xyz";
        
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL(expectedUrl));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        // Act
        String url = s3Service.generatePresignedUrl(testFileId);

        // Assert
        assertNotNull(url);
        assertEquals(expectedUrl, url);

        // Verify presigner was called with correct parameters
        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = 
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(requestCaptor.capture());
        
        GetObjectPresignRequest capturedRequest = requestCaptor.getValue();
        assertEquals(Duration.ofHours(1), capturedRequest.signatureDuration());
        assertEquals(TEST_BUCKET, capturedRequest.getObjectRequest().bucket());
        assertEquals(testFileId, capturedRequest.getObjectRequest().key());
    }

    @Test
    void testGeneratePresignedUrl_S3Exception() {
        // Arrange
        String testFileId = "audio/user123/12345-uuid.mp3";
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());

        // Act
        String url = s3Service.generatePresignedUrl(testFileId);

        // Assert
        assertNull(url, "URL should be null on S3 exception");
    }

    @Test
    void testDeleteAudioFile_Success() {
        // Arrange
        String testFileId = "audio/user123/12345-uuid.mp3";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        // Act
        boolean result = s3Service.deleteAudioFile(testFileId);

        // Assert
        assertTrue(result, "Delete should return true on success");

        // Verify S3 client was called
        ArgumentCaptor<DeleteObjectRequest> requestCaptor = 
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        
        DeleteObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TEST_BUCKET, capturedRequest.bucket());
        assertEquals(testFileId, capturedRequest.key());
    }

    @Test
    void testDeleteAudioFile_S3Exception() {
        // Arrange
        String testFileId = "audio/user123/12345-uuid.mp3";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());

        // Act
        boolean result = s3Service.deleteAudioFile(testFileId);

        // Assert
        assertFalse(result, "Delete should return false on S3 exception");
    }

    @Test
    void testFileExists_True() {
        // Arrange
        String testFileId = "audio/user123/12345-uuid.mp3";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        // Act
        boolean exists = s3Service.fileExists(testFileId);

        // Assert
        assertTrue(exists, "File should exist");

        // Verify S3 client was called
        ArgumentCaptor<HeadObjectRequest> requestCaptor = 
                ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(requestCaptor.capture());
        
        HeadObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TEST_BUCKET, capturedRequest.bucket());
        assertEquals(testFileId, capturedRequest.key());
    }

    @Test
    void testFileExists_False() {
        // Arrange
        String testFileId = "audio/user123/nonexistent.mp3";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        // Act
        boolean exists = s3Service.fileExists(testFileId);

        // Assert
        assertFalse(exists, "File should not exist");
    }

    @Test
    void testFileExists_S3Exception() {
        // Arrange
        String testFileId = "audio/user123/12345-uuid.mp3";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());

        // Act
        boolean exists = s3Service.fileExists(testFileId);

        // Assert
        assertFalse(exists, "File existence check should return false on S3 exception");
    }

    @Test
    void testGetPresignedUrlExpiration() {
        // Act
        Duration expiration = s3Service.getPresignedUrlExpiration();

        // Assert
        assertNotNull(expiration);
        assertEquals(Duration.ofHours(1), expiration);
    }

    @Test
    void testUploadAndGenerateUrl_Integration() throws Exception {
        // Arrange
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        
        String expectedUrl = "https://test-bucket.s3.amazonaws.com/audio/user123/file.mp3?signature=xyz";
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL(expectedUrl));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        // Act
        String fileId = s3Service.uploadAudioFile(TEST_AUDIO_DATA, "mp3", TEST_USER_ID);
        String url = s3Service.generatePresignedUrl(fileId);

        // Assert
        assertNotNull(fileId);
        assertNotNull(url);
        assertEquals(expectedUrl, url);
    }
}

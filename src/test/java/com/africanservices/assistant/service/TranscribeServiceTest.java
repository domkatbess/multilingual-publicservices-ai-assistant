package com.africanservices.assistant.service;

import com.africanservices.assistant.util.StructuredLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TranscribeService.
 * 
 * Validates Requirements: 2.3, 2.4, 2.5, 9.3, 11.6
 */
@ExtendWith(MockitoExtension.class)
class TranscribeServiceTest {

    @Mock
    private TranscribeClient transcribeClient;

    @Mock
    private S3Service s3Service;

    @Mock
    private StructuredLogger logger;

    private TranscribeService transcribeService;
    
    private static final String AUDIO_BUCKET = "test-audio-bucket";
    private static final long POLLING_INTERVAL_MS = 100; // Short interval for tests
    private static final int MAX_POLLING_ATTEMPTS = 3;

    @BeforeEach
    void setUp() {
        transcribeService = new TranscribeService(
                transcribeClient, 
                s3Service, 
                logger,
                AUDIO_BUCKET,
                POLLING_INTERVAL_MS,
                MAX_POLLING_ATTEMPTS
        );
    }

    @Test
    void testSubmitTranscriptionJob_Success() {
        // Arrange
        String fileId = "audio/user123/12345-uuid.mp3";
        String audioFormat = "mp3";
        String languageCode = "en-US";

        StartTranscriptionJobResponse response = StartTranscriptionJobResponse.builder().build();
        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenReturn(response);

        // Act
        String jobName = transcribeService.submitTranscriptionJob(fileId, audioFormat, languageCode);

        // Assert
        assertNotNull(jobName);
        assertTrue(jobName.startsWith("transcribe_"));
        assertTrue(jobName.contains("audio_user123"));

        ArgumentCaptor<StartTranscriptionJobRequest> captor = 
                ArgumentCaptor.forClass(StartTranscriptionJobRequest.class);
        verify(transcribeClient).startTranscriptionJob(captor.capture());

        StartTranscriptionJobRequest request = captor.getValue();
        assertEquals(MediaFormat.MP3, request.mediaFormat());
        assertEquals(languageCode, request.languageCodeAsString());
        assertTrue(request.media().mediaFileUri().contains(fileId));

        verify(logger).info(eq("Transcription job submitted: jobName={}, fileId={}, languageCode={}"), 
                anyString(), eq(fileId), eq(languageCode));
    }

    @Test
    void testSubmitTranscriptionJob_WavFormat() {
        // Arrange
        String fileId = "audio/user123/12345-uuid.wav";
        String audioFormat = "wav";
        String languageCode = "ha-NG";

        StartTranscriptionJobResponse response = StartTranscriptionJobResponse.builder().build();
        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenReturn(response);

        // Act
        String jobName = transcribeService.submitTranscriptionJob(fileId, audioFormat, languageCode);

        // Assert
        assertNotNull(jobName);

        ArgumentCaptor<StartTranscriptionJobRequest> captor = 
                ArgumentCaptor.forClass(StartTranscriptionJobRequest.class);
        verify(transcribeClient).startTranscriptionJob(captor.capture());

        StartTranscriptionJobRequest request = captor.getValue();
        assertEquals(MediaFormat.WAV, request.mediaFormat());
    }

    @Test
    void testSubmitTranscriptionJob_M4aFormat() {
        // Arrange
        String fileId = "audio/user123/12345-uuid.m4a";
        String audioFormat = "m4a";
        String languageCode = "yo-NG";

        StartTranscriptionJobResponse response = StartTranscriptionJobResponse.builder().build();
        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenReturn(response);

        // Act
        String jobName = transcribeService.submitTranscriptionJob(fileId, audioFormat, languageCode);

        // Assert
        assertNotNull(jobName);

        ArgumentCaptor<StartTranscriptionJobRequest> captor = 
                ArgumentCaptor.forClass(StartTranscriptionJobRequest.class);
        verify(transcribeClient).startTranscriptionJob(captor.capture());

        StartTranscriptionJobRequest request = captor.getValue();
        assertEquals(MediaFormat.MP4, request.mediaFormat());
    }

    @Test
    void testSubmitTranscriptionJob_Failure() {
        // Arrange
        String fileId = "audio/user123/12345-uuid.mp3";
        String audioFormat = "mp3";
        String languageCode = "en-US";

        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenThrow(TranscribeException.builder().message("Service unavailable").build());

        // Act
        String jobName = transcribeService.submitTranscriptionJob(fileId, audioFormat, languageCode);

        // Assert
        assertNull(jobName);
        verify(logger).warn(eq("Failed to submit transcription job: fileId={}, error={}"), 
                eq(fileId), anyString());
    }

    @Test
    void testTranscribeAudio_SubmissionFailure() {
        // Arrange
        String fileId = "audio/user123/12345-uuid.mp3";
        String audioFormat = "mp3";
        String languageCode = "en-US";

        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenThrow(TranscribeException.builder().message("Service unavailable").build());

        // Act
        String result = transcribeService.transcribeAudio(fileId, audioFormat, languageCode);

        // Assert
        assertNull(result);
        verify(s3Service, never()).deleteAudioFile(any());
    }
}

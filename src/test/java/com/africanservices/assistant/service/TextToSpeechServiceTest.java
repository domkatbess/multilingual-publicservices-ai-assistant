package com.africanservices.assistant.service;

import com.africanservices.assistant.util.MetricsEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TextToSpeechService.
 * Tests TTS conversion, S3 integration, graceful degradation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class TextToSpeechServiceTest {

    @Mock
    private PollyService pollyService;

    @Mock
    private S3Service s3Service;

    @Mock
    private MetricsEmitter metricsEmitter;

    private TextToSpeechService ttsService;

    @BeforeEach
    void setUp() {
        ttsService = new TextToSpeechService(pollyService, s3Service, metricsEmitter);
    }

    @Test
    void testConvertTextToSpeech_Success() {
        // Arrange
        String text = "Hello, how can I help you?";
        String language = "en";
        String userId = "user123";
        byte[] audioData = new byte[]{1, 2, 3, 4, 5};
        String fileId = "audio/user123/12345-uuid.mp3";
        String audioUrl = "https://s3.amazonaws.com/bucket/audio/user123/12345-uuid.mp3";
        
        when(pollyService.synthesizeSpeech(text, language)).thenReturn(audioData);
        when(s3Service.uploadAudioFile(audioData, "mp3", userId)).thenReturn(fileId);
        when(s3Service.generatePresignedUrl(fileId)).thenReturn(audioUrl);
        when(s3Service.getPresignedUrlExpiration()).thenReturn(Duration.ofHours(1));

        // Act
        TextToSpeechService.TTSResult result = ttsService.convertTextToSpeech(text, language, userId);

        // Assert
        assertNotNull(result);
        assertEquals(audioUrl, result.getAudioUrl());
        assertNotNull(result.getExpiration());
        assertTrue(result.getExpiration().isAfter(Instant.now()));
        
        verify(pollyService, times(1)).synthesizeSpeech(text, language);
        verify(s3Service, times(1)).uploadAudioFile(audioData, "mp3", userId);
        verify(s3Service, times(1)).generatePresignedUrl(fileId);
        verify(metricsEmitter, times(1)).emitMetric(eq("TTSSuccess"), eq(1.0), any(), eq(language), isNull());
        verify(metricsEmitter, times(1)).emitMetric(eq("TTSLatency"), anyDouble(), any(), eq(language), isNull());
    }

    @Test
    void testConvertTextToSpeech_PollySynthesisFails() {
        // Arrange
        String text = "Test text";
        String language = "en";
        String userId = "user123";
        
        when(pollyService.synthesizeSpeech(text, language)).thenReturn(null);

        // Act
        TextToSpeechService.TTSResult result = ttsService.convertTextToSpeech(text, language, userId);

        // Assert
        assertNull(result); // Graceful degradation
        verify(pollyService, times(1)).synthesizeSpeech(text, language);
        verify(s3Service, never()).uploadAudioFile(any(), any(), any());
        verify(metricsEmitter, times(1)).emitMetric(eq("TTSFailure"), eq(1.0), any(), eq(language), isNull());
    }

    @Test
    void testConvertTextToSpeech_S3UploadFails() {
        // Arrange
        String text = "Test text";
        String language = "en";
        String userId = "user123";
        byte[] audioData = new byte[]{1, 2, 3};
        
        when(pollyService.synthesizeSpeech(text, language)).thenReturn(audioData);
        when(s3Service.uploadAudioFile(audioData, "mp3", userId)).thenReturn(null);

        // Act
        TextToSpeechService.TTSResult result = ttsService.convertTextToSpeech(text, language, userId);

        // Assert
        assertNull(result); // Graceful degradation
        verify(pollyService, times(1)).synthesizeSpeech(text, language);
        verify(s3Service, times(1)).uploadAudioFile(audioData, "mp3", userId);
        verify(s3Service, never()).generatePresignedUrl(any());
        verify(metricsEmitter, times(1)).emitMetric(eq("TTSFailure"), eq(1.0), any(), eq(language), isNull());
    }

    @Test
    void testConvertTextToSpeech_PresignedUrlFails() {
        // Arrange
        String text = "Test text";
        String language = "en";
        String userId = "user123";
        byte[] audioData = new byte[]{1, 2, 3};
        String fileId = "audio/user123/12345-uuid.mp3";
        
        when(pollyService.synthesizeSpeech(text, language)).thenReturn(audioData);
        when(s3Service.uploadAudioFile(audioData, "mp3", userId)).thenReturn(fileId);
        when(s3Service.generatePresignedUrl(fileId)).thenReturn(null);

        // Act
        TextToSpeechService.TTSResult result = ttsService.convertTextToSpeech(text, language, userId);

        // Assert
        assertNull(result); // Graceful degradation
        verify(pollyService, times(1)).synthesizeSpeech(text, language);
        verify(s3Service, times(1)).uploadAudioFile(audioData, "mp3", userId);
        verify(s3Service, times(1)).generatePresignedUrl(fileId);
        verify(s3Service, times(1)).deleteAudioFile(fileId); // Cleanup
        verify(metricsEmitter, times(1)).emitMetric(eq("TTSFailure"), eq(1.0), any(), eq(language), isNull());
    }

    @Test
    void testConvertTextToSpeech_MultipleLanguages() {
        // Arrange
        byte[] audioData = new byte[]{1, 2, 3};
        String fileId = "audio/user123/12345-uuid.mp3";
        String audioUrl = "https://s3.amazonaws.com/bucket/audio.mp3";
        
        when(pollyService.synthesizeSpeech(anyString(), anyString())).thenReturn(audioData);
        when(s3Service.uploadAudioFile(any(), anyString(), anyString())).thenReturn(fileId);
        when(s3Service.generatePresignedUrl(anyString())).thenReturn(audioUrl);
        when(s3Service.getPresignedUrlExpiration()).thenReturn(Duration.ofHours(1));

        // Act & Assert
        assertNotNull(ttsService.convertTextToSpeech("Test", "ha", "user123"));
        assertNotNull(ttsService.convertTextToSpeech("Test", "yo", "user123"));
        assertNotNull(ttsService.convertTextToSpeech("Test", "ig", "user123"));
        assertNotNull(ttsService.convertTextToSpeech("Test", "ff", "user123"));
        assertNotNull(ttsService.convertTextToSpeech("Test", "en", "user123"));
        
        verify(pollyService, times(5)).synthesizeSpeech(anyString(), anyString());
    }

    @Test
    void testConvertTextToSpeech_ExpirationIsOneHour() {
        // Arrange
        String text = "Test";
        String language = "en";
        String userId = "user123";
        byte[] audioData = new byte[]{1, 2, 3};
        String fileId = "audio/user123/12345-uuid.mp3";
        String audioUrl = "https://s3.amazonaws.com/bucket/audio.mp3";
        
        when(pollyService.synthesizeSpeech(text, language)).thenReturn(audioData);
        when(s3Service.uploadAudioFile(audioData, "mp3", userId)).thenReturn(fileId);
        when(s3Service.generatePresignedUrl(fileId)).thenReturn(audioUrl);
        when(s3Service.getPresignedUrlExpiration()).thenReturn(Duration.ofHours(1));

        // Act
        Instant beforeCall = Instant.now();
        TextToSpeechService.TTSResult result = ttsService.convertTextToSpeech(text, language, userId);
        Instant afterCall = Instant.now();

        // Assert
        assertNotNull(result);
        Instant expiration = result.getExpiration();
        
        // Expiration should be approximately 1 hour from now
        Instant expectedMin = beforeCall.plus(Duration.ofHours(1));
        Instant expectedMax = afterCall.plus(Duration.ofHours(1));
        
        assertTrue(expiration.isAfter(expectedMin.minusSeconds(1)));
        assertTrue(expiration.isBefore(expectedMax.plusSeconds(1)));
    }

    @Test
    void testConvertTextToSpeech_UnexpectedException() {
        // Arrange
        String text = "Test";
        String language = "en";
        String userId = "user123";
        
        when(pollyService.synthesizeSpeech(text, language))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        TextToSpeechService.TTSResult result = ttsService.convertTextToSpeech(text, language, userId);

        // Assert
        assertNull(result); // Graceful degradation
        verify(metricsEmitter, times(1)).emitMetric(eq("TTSFailure"), eq(1.0), any(), eq(language), isNull());
    }

    @Test
    void testTTSResult_GettersWork() {
        // Arrange
        String audioUrl = "https://example.com/audio.mp3";
        Instant expiration = Instant.now().plusSeconds(3600);

        // Act
        TextToSpeechService.TTSResult result = new TextToSpeechService.TTSResult(audioUrl, expiration);

        // Assert
        assertEquals(audioUrl, result.getAudioUrl());
        assertEquals(expiration, result.getExpiration());
        assertEquals(expiration.getEpochSecond(), result.getExpirationEpochSeconds());
    }
}

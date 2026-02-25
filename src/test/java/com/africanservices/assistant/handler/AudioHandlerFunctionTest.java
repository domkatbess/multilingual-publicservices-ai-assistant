package com.africanservices.assistant.handler;

import com.africanservices.assistant.model.AudioRequest;
import com.africanservices.assistant.model.ErrorResponse;
import com.africanservices.assistant.model.SuccessResponse;
import com.africanservices.assistant.service.*;
import com.africanservices.assistant.service.ResponseGeneratorService.ResponseGenerationException;
import com.africanservices.assistant.service.ServiceCategoryClassifier.ServiceCategory;
import com.africanservices.assistant.service.SessionManagerService.SessionData;
import com.africanservices.assistant.service.TextToSpeechService.TTSResult;
import com.africanservices.assistant.util.MetricsEmitter;
import com.africanservices.assistant.validation.InputValidator;
import com.africanservices.assistant.validation.InputValidator.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AudioHandlerFunction.
 * Tests audio processing, transcription, and integration with text processing logic.
 */
@ExtendWith(MockitoExtension.class)
class AudioHandlerFunctionTest {

    @Mock
    private InputValidator inputValidator;
    
    @Mock
    private S3Service s3Service;
    
    @Mock
    private TranscribeService transcribeService;
    
    @Mock
    private LanguageDetectionService languageDetectionService;
    
    @Mock
    private ServiceCategoryClassifier serviceCategoryClassifier;
    
    @Mock
    private SessionManagerService sessionManagerService;
    
    @Mock
    private CacheManagerService cacheManagerService;
    
    @Mock
    private ResponseGeneratorService responseGeneratorService;
    
    @Mock
    private TextToSpeechService textToSpeechService;
    
    @Mock
    private MetricsEmitter metricsEmitter;
    
    @Mock
    private Validator validator;
    
    private ObjectMapper objectMapper;
    
    private AudioHandlerFunction audioHandlerFunction;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        audioHandlerFunction = new AudioHandlerFunction(
                inputValidator,
                s3Service,
                transcribeService,
                languageDetectionService,
                serviceCategoryClassifier,
                sessionManagerService,
                cacheManagerService,
                responseGeneratorService,
                textToSpeechService,
                metricsEmitter,
                validator,
                objectMapper
        );
    }

    @Test
    void testSuccessfulAudioProcessing() throws ResponseGenerationException {
        // Arrange
        String base64Audio = Base64.getEncoder().encodeToString("test audio data".getBytes());
        AudioRequest request = new AudioRequest(
                "user123",
                "session123",
                base64Audio,
                "mp3"
        );
        
        when(validator.validate(request)).thenReturn(Collections.emptySet());
        when(inputValidator.validateAudioFormat("mp3"))
                .thenReturn(new ValidationResult(true, Collections.emptyList()));
        when(s3Service.uploadAudioFile(any(byte[].class), eq("mp3"), eq("user123")))
                .thenReturn("audio/user123/12345-uuid.mp3");
        when(transcribeService.transcribeAudio(anyString(), eq("mp3"), anyString()))
                .thenReturn("Hello, how can I help you?");
        
        SessionData sessionData = new SessionData(
                "session123", "user123", "en", System.currentTimeMillis(),
                new HashMap<>(), new ArrayList<>()
        );
        when(sessionManagerService.getSession("session123"))
                .thenReturn(Optional.of(sessionData));
        when(serviceCategoryClassifier.classifyQuery(anyString()))
                .thenReturn(ServiceCategory.GOVERNMENT);
        when(cacheManagerService.getCachedResponse(anyString(), eq("en")))
                .thenReturn(Optional.empty());
        when(responseGeneratorService.generateResponse(anyString(), anyString(), eq("en"), any()))
                .thenReturn("I can help you with government services.");
        
        TTSResult ttsResult = new TTSResult(
                "https://s3.amazonaws.com/audio.mp3",
                Instant.now().plusSeconds(3600)
        );
        when(textToSpeechService.convertTextToSpeech(anyString(), eq("en"), eq("user123")))
                .thenReturn(ttsResult);
        
        // Act
        Object result = audioHandlerFunction.apply(request);
        
        // Assert
        assertNotNull(result);
        assertTrue(result instanceof SuccessResponse);
        SuccessResponse response = (SuccessResponse) result;
        assertEquals("I can help you with government services.", response.responseText());
        assertEquals("en", response.detectedLanguage());
        assertEquals("GOVERNMENT", response.intent());
        assertNotNull(response.audioUrl());
        
        verify(s3Service).uploadAudioFile(any(byte[].class), eq("mp3"), eq("user123"));
        verify(transcribeService).transcribeAudio(anyString(), eq("mp3"), anyString());
        verify(sessionManagerService).updateSession(eq("session123"), anyString(), anyString());
        verify(metricsEmitter, atLeastOnce()).emitMetric(anyString(), anyDouble(), any());
    }

    @Test
    void testInvalidAudioFormat() {
        // Arrange
        String base64Audio = Base64.getEncoder().encodeToString("test audio data".getBytes());
        AudioRequest request = new AudioRequest(
                "user123",
                "session123",
                base64Audio,
                "ogg"
        );
        
        when(validator.validate(request)).thenReturn(Collections.emptySet());
        when(inputValidator.validateAudioFormat("ogg"))
                .thenReturn(new ValidationResult(false, List.of("Audio format must be WAV, MP3, or M4A")));
        
        // Act
        Object result = audioHandlerFunction.apply(request);
        
        // Assert
        assertNotNull(result);
        assertTrue(result instanceof ErrorResponse);
        ErrorResponse response = (ErrorResponse) result;
        assertEquals("INVALID_AUDIO_FORMAT", response.errorCode());
        assertFalse(response.retryable());
        
        verify(s3Service, never()).uploadAudioFile(any(), any(), any());
        verify(transcribeService, never()).transcribeAudio(any(), any(), any());
    }

    @Test
    void testInvalidBase64AudioData() {
        // Arrange
        AudioRequest request = new AudioRequest(
                "user123",
                "session123",
                "invalid-base64!!!",
                "mp3"
        );
        
        when(validator.validate(request)).thenReturn(Collections.emptySet());
        when(inputValidator.validateAudioFormat("mp3"))
                .thenReturn(new ValidationResult(true, Collections.emptyList()));
        
        // Act
        Object result = audioHandlerFunction.apply(request);
        
        // Assert
        assertNotNull(result);
        assertTrue(result instanceof ErrorResponse);
        ErrorResponse response = (ErrorResponse) result;
        assertEquals("INVALID_AUDIO_DATA", response.errorCode());
        assertFalse(response.retryable());
        
        verify(s3Service, never()).uploadAudioFile(any(), any(), any());
    }

    @Test
    void testS3UploadFailure() {
        // Arrange
        String base64Audio = Base64.getEncoder().encodeToString("test audio data".getBytes());
        AudioRequest request = new AudioRequest(
                "user123",
                "session123",
                base64Audio,
                "mp3"
        );
        
        when(validator.validate(request)).thenReturn(Collections.emptySet());
        when(inputValidator.validateAudioFormat("mp3"))
                .thenReturn(new ValidationResult(true, Collections.emptyList()));
        when(s3Service.uploadAudioFile(any(byte[].class), eq("mp3"), eq("user123")))
                .thenReturn(null);
        
        // Act
        Object result = audioHandlerFunction.apply(request);
        
        // Assert
        assertNotNull(result);
        assertTrue(result instanceof ErrorResponse);
        ErrorResponse response = (ErrorResponse) result;
        assertEquals("STORAGE_ERROR", response.errorCode());
        assertTrue(response.retryable());
        
        verify(transcribeService, never()).transcribeAudio(any(), any(), any());
    }

    @Test
    void testTranscriptionFailure() {
        // Arrange
        String base64Audio = Base64.getEncoder().encodeToString("test audio data".getBytes());
        AudioRequest request = new AudioRequest(
                "user123",
                "session123",
                base64Audio,
                "mp3"
        );
        
        when(validator.validate(request)).thenReturn(Collections.emptySet());
        when(inputValidator.validateAudioFormat("mp3"))
                .thenReturn(new ValidationResult(true, Collections.emptyList()));
        when(s3Service.uploadAudioFile(any(byte[].class), eq("mp3"), eq("user123")))
                .thenReturn("audio/user123/12345-uuid.mp3");
        when(transcribeService.transcribeAudio(anyString(), eq("mp3"), anyString()))
                .thenReturn(null);
        
        // Act
        Object result = audioHandlerFunction.apply(request);
        
        // Assert
        assertNotNull(result);
        assertTrue(result instanceof ErrorResponse);
        ErrorResponse response = (ErrorResponse) result;
        assertEquals("TRANSCRIPTION_FAILED", response.errorCode());
        assertTrue(response.retryable());
        
        verify(s3Service).deleteAudioFile("audio/user123/12345-uuid.mp3");
        verify(metricsEmitter).emitMetric(eq("TranscriptionFailure"), anyDouble(), any());
    }

    @Test
    void testNewSessionCreation() throws ResponseGenerationException {
        // Arrange
        String base64Audio = Base64.getEncoder().encodeToString("test audio data".getBytes());
        AudioRequest request = new AudioRequest(
                "user123",
                "session123",
                base64Audio,
                "wav"
        );
        
        when(validator.validate(request)).thenReturn(Collections.emptySet());
        when(inputValidator.validateAudioFormat("wav"))
                .thenReturn(new ValidationResult(true, Collections.emptyList()));
        when(s3Service.uploadAudioFile(any(byte[].class), eq("wav"), eq("user123")))
                .thenReturn("audio/user123/12345-uuid.wav");
        when(transcribeService.transcribeAudio(anyString(), eq("wav"), anyString()))
                .thenReturn("Sannu, ina iya taimaka muku?");
        
        when(sessionManagerService.getSession("session123"))
                .thenReturn(Optional.empty());
        when(languageDetectionService.detectLanguage(anyString()))
                .thenReturn("ha");
        when(serviceCategoryClassifier.classifyQuery(anyString()))
                .thenReturn(ServiceCategory.HEALTH);
        when(cacheManagerService.getCachedResponse(anyString(), eq("ha")))
                .thenReturn(Optional.empty());
        when(responseGeneratorService.generateResponse(anyString(), anyString(), eq("ha"), any()))
                .thenReturn("Zan iya taimaka muku da sabis na lafiya.");
        when(textToSpeechService.convertTextToSpeech(anyString(), eq("ha"), eq("user123")))
                .thenReturn(null); // Graceful degradation
        
        // Act
        Object result = audioHandlerFunction.apply(request);
        
        // Assert
        assertNotNull(result);
        assertTrue(result instanceof SuccessResponse);
        SuccessResponse response = (SuccessResponse) result;
        assertEquals("ha", response.detectedLanguage());
        assertEquals("HEALTH", response.intent());
        assertNull(response.audioUrl()); // TTS failed, graceful degradation
        
        verify(sessionManagerService).createSession(eq("user123"), eq("ha"));
        verify(languageDetectionService).detectLanguage(anyString());
    }

    @Test
    void testCacheHit() throws ResponseGenerationException {
        // Arrange
        String base64Audio = Base64.getEncoder().encodeToString("test audio data".getBytes());
        AudioRequest request = new AudioRequest(
                "user123",
                "session123",
                base64Audio,
                "m4a"
        );
        
        when(validator.validate(request)).thenReturn(Collections.emptySet());
        when(inputValidator.validateAudioFormat("m4a"))
                .thenReturn(new ValidationResult(true, Collections.emptyList()));
        when(s3Service.uploadAudioFile(any(byte[].class), eq("m4a"), eq("user123")))
                .thenReturn("audio/user123/12345-uuid.m4a");
        when(transcribeService.transcribeAudio(anyString(), eq("m4a"), anyString()))
                .thenReturn("What are the school enrollment requirements?");
        
        SessionData sessionData = new SessionData(
                "session123", "user123", "en", System.currentTimeMillis(),
                new HashMap<>(), new ArrayList<>()
        );
        when(sessionManagerService.getSession("session123"))
                .thenReturn(Optional.of(sessionData));
        when(serviceCategoryClassifier.classifyQuery(anyString()))
                .thenReturn(ServiceCategory.EDUCATION);
        when(cacheManagerService.getCachedResponse(anyString(), eq("en")))
                .thenReturn(Optional.of("Cached response about enrollment requirements"));
        when(textToSpeechService.convertTextToSpeech(anyString(), eq("en"), eq("user123")))
                .thenReturn(null);
        
        // Act
        Object result = audioHandlerFunction.apply(request);
        
        // Assert
        assertNotNull(result);
        assertTrue(result instanceof SuccessResponse);
        SuccessResponse response = (SuccessResponse) result;
        assertEquals("Cached response about enrollment requirements", response.responseText());
        
        verify(responseGeneratorService, never()).generateResponse(any(), any(), any(), any());
        verify(metricsEmitter).emitCacheHit("en");
    }

    @Test
    void testValidationError() {
        // Arrange
        AudioRequest request = new AudioRequest(
                "",
                "session123",
                "base64data",
                "mp3"
        );
        
        Set<ConstraintViolation<AudioRequest>> violations = new HashSet<>();
        ConstraintViolation<AudioRequest> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("User ID is required");
        violations.add(violation);
        
        when(validator.validate(request)).thenReturn(violations);
        
        // Act
        Object result = audioHandlerFunction.apply(request);
        
        // Assert
        assertNotNull(result);
        assertTrue(result instanceof ErrorResponse);
        ErrorResponse response = (ErrorResponse) result;
        assertEquals("VALIDATION_ERROR", response.errorCode());
        assertFalse(response.retryable());
        
        verify(s3Service, never()).uploadAudioFile(any(), any(), any());
    }
}

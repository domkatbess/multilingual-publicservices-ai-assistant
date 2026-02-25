package com.africanservices.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PollyService.
 * Tests voice selection, engine selection, cost optimization, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class PollyServiceTest {

    @Mock
    private PollyClient pollyClient;

    private PollyService pollyService;

    @BeforeEach
    void setUp() {
        pollyService = new PollyService(pollyClient);
    }

    @Test
    void testSynthesizeSpeech_Success() {
        // Arrange
        String text = "Hello, how can I help you?";
        String language = "en";
        byte[] expectedAudio = new byte[]{1, 2, 3, 4, 5};
        
        ResponseInputStream<SynthesizeSpeechResponse> mockResponse = 
                createMockResponse(expectedAudio);
        
        when(pollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class)))
                .thenReturn(mockResponse);

        // Act
        byte[] result = pollyService.synthesizeSpeech(text, language);

        // Assert
        assertNotNull(result);
        assertArrayEquals(expectedAudio, result);
        verify(pollyClient, times(1)).synthesizeSpeech(any(SynthesizeSpeechRequest.class));
    }

    @Test
    void testSynthesizeSpeech_PollyException() {
        // Arrange
        String text = "Test text";
        String language = "en";
        
        when(pollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class)))
                .thenThrow(PollyException.builder().message("Polly error").build());

        // Act
        byte[] result = pollyService.synthesizeSpeech(text, language);

        // Assert
        assertNull(result);
        verify(pollyClient, times(1)).synthesizeSpeech(any(SynthesizeSpeechRequest.class));
    }

    @Test
    void testVoiceSelection_Hausa() {
        // Act
        String voice = pollyService.getVoiceForLanguage("ha");

        // Assert
        assertEquals("Zeina", voice);
    }

    @Test
    void testVoiceSelection_Yoruba() {
        // Act
        String voice = pollyService.getVoiceForLanguage("yo");

        // Assert
        assertEquals("Joanna", voice);
    }

    @Test
    void testVoiceSelection_Igbo() {
        // Act
        String voice = pollyService.getVoiceForLanguage("ig");

        // Assert
        assertEquals("Joanna", voice);
    }

    @Test
    void testVoiceSelection_Fulfulde() {
        // Act
        String voice = pollyService.getVoiceForLanguage("ff");

        // Assert
        assertEquals("Zeina", voice);
    }

    @Test
    void testVoiceSelection_English() {
        // Act
        String voice = pollyService.getVoiceForLanguage("en");

        // Assert
        assertEquals("Joanna", voice);
    }

    @Test
    void testVoiceSelection_UnsupportedLanguage() {
        // Act
        String voice = pollyService.getVoiceForLanguage("fr");

        // Assert
        assertEquals("Joanna", voice); // Default fallback
    }

    @Test
    void testNeuralEngineSupport_Joanna() {
        // Act
        boolean supports = pollyService.supportsNeuralEngine("Joanna");

        // Assert
        assertTrue(supports);
    }

    @Test
    void testNeuralEngineSupport_Zeina() {
        // Act
        boolean supports = pollyService.supportsNeuralEngine("Zeina");

        // Assert
        assertTrue(supports);
    }

    @Test
    void testNeuralEngineSupport_UnsupportedVoice() {
        // Act
        boolean supports = pollyService.supportsNeuralEngine("UnknownVoice");

        // Assert
        assertFalse(supports);
    }

    @Test
    void testSynthesizeSpeech_MultipleLanguages() {
        // Arrange
        byte[] audioData = new byte[]{1, 2, 3};
        ResponseInputStream<SynthesizeSpeechResponse> mockResponse = 
                createMockResponse(audioData);
        
        when(pollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class)))
                .thenReturn(mockResponse);

        // Act & Assert
        assertNotNull(pollyService.synthesizeSpeech("Test", "ha"));
        assertNotNull(pollyService.synthesizeSpeech("Test", "yo"));
        assertNotNull(pollyService.synthesizeSpeech("Test", "ig"));
        assertNotNull(pollyService.synthesizeSpeech("Test", "ff"));
        assertNotNull(pollyService.synthesizeSpeech("Test", "en"));
        
        verify(pollyClient, times(5)).synthesizeSpeech(any(SynthesizeSpeechRequest.class));
    }

    @Test
    void testSynthesizeSpeech_EmptyText() {
        // Arrange
        byte[] audioData = new byte[]{};
        ResponseInputStream<SynthesizeSpeechResponse> mockResponse = 
                createMockResponse(audioData);
        
        when(pollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class)))
                .thenReturn(mockResponse);

        // Act
        byte[] result = pollyService.synthesizeSpeech("", "en");

        // Assert
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    // Helper method to create mock response
    private ResponseInputStream<SynthesizeSpeechResponse> createMockResponse(byte[] audioData) {
        SynthesizeSpeechResponse response = SynthesizeSpeechResponse.builder().build();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(audioData);
        return new ResponseInputStream<>(response, inputStream);
    }
}

package com.africanservices.assistant.service;

import com.africanservices.assistant.util.ErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GracefulDegradationServiceTest {

    @Mock
    private CacheManagerService cacheManagerService;

    @Mock
    private ErrorHandler errorHandler;

    private GracefulDegradationService gracefulDegradationService;

    @BeforeEach
    void setUp() {
        gracefulDegradationService = new GracefulDegradationService(
                cacheManagerService, errorHandler);
    }

    @Test
    void testHandleBedrockUnavailable_WithCachedResponse() {
        String query = "How do I apply for a passport?";
        String language = "en";
        String serviceCategory = "Government";
        String cachedResponse = "To apply for a passport, visit your local passport office...";

        when(cacheManagerService.getCachedResponse(query, language))
                .thenReturn(Optional.of(cachedResponse));

        String result = gracefulDegradationService.handleBedrockUnavailable(
                query, language, serviceCategory);

        assertEquals(cachedResponse, result);
        verify(cacheManagerService).getCachedResponse(query, language);
    }

    @Test
    void testHandleBedrockUnavailable_WithoutCache_UsesFallback() {
        String query = "How do I apply for a passport?";
        String language = "en";
        String serviceCategory = "Government";

        when(cacheManagerService.getCachedResponse(query, language))
                .thenReturn(Optional.empty());

        String result = gracefulDegradationService.handleBedrockUnavailable(
                query, language, serviceCategory);

        assertNotNull(result);
        assertTrue(result.contains("government") || result.contains("Government"));
        verify(cacheManagerService).getCachedResponse(query, language);
    }

    @Test
    void testHandleBedrockUnavailable_CacheThrowsException_UsesFallback() {
        String query = "How do I apply for a passport?";
        String language = "en";
        String serviceCategory = "Government";

        when(cacheManagerService.getCachedResponse(query, language))
                .thenThrow(new RuntimeException("Cache error"));

        String result = gracefulDegradationService.handleBedrockUnavailable(
                query, language, serviceCategory);

        assertNotNull(result);
        assertTrue(result.contains("government") || result.contains("Government"));
    }

    @Test
    void testHandleBedrockUnavailable_AllServiceCategories() {
        String[] categories = {"Government", "Health", "Education", "Emergency"};
        
        when(cacheManagerService.getCachedResponse(anyString(), anyString()))
                .thenReturn(Optional.empty());

        for (String category : categories) {
            String result = gracefulDegradationService.handleBedrockUnavailable(
                    "test query", "en", category);
            
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    void testHandleBedrockUnavailable_AllLanguages() {
        String[] languages = {"en", "ha", "yo", "ig", "ff"};
        
        when(cacheManagerService.getCachedResponse(anyString(), anyString()))
                .thenReturn(Optional.empty());

        for (String language : languages) {
            String result = gracefulDegradationService.handleBedrockUnavailable(
                    "test query", language, "Government");
            
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    void testHandleBedrockUnavailable_UnsupportedLanguage_FallsBackToEnglish() {
        when(cacheManagerService.getCachedResponse(anyString(), anyString()))
                .thenReturn(Optional.empty());

        String result = gracefulDegradationService.handleBedrockUnavailable(
                "test query", "fr", "Government");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Should return English fallback
    }

    @Test
    void testHandlePollyUnavailable_ReturnsTextResponse() {
        String responseText = "This is the response text";
        String language = "en";

        String result = gracefulDegradationService.handlePollyUnavailable(
                responseText, language);

        assertEquals(responseText, result);
    }

    @Test
    void testHandleTranscribeFailure_English() {
        String result = gracefulDegradationService.handleTranscribeFailure("en");

        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("type") || result.toLowerCase().contains("speech"));
    }

    @Test
    void testHandleTranscribeFailure_Hausa() {
        String result = gracefulDegradationService.handleTranscribeFailure("ha");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testHandleTranscribeFailure_Yoruba() {
        String result = gracefulDegradationService.handleTranscribeFailure("yo");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testHandleTranscribeFailure_Igbo() {
        String result = gracefulDegradationService.handleTranscribeFailure("ig");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testHandleTranscribeFailure_Fulfulde() {
        String result = gracefulDegradationService.handleTranscribeFailure("ff");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testHandleTranscribeFailure_UnsupportedLanguage_ReturnsEnglish() {
        String result = gracefulDegradationService.handleTranscribeFailure("fr");

        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("type") || result.toLowerCase().contains("speech"));
    }

    @Test
    void testHandleLanguageDetectionFailure_ReturnsEnglish() {
        String result = gracefulDegradationService.handleLanguageDetectionFailure();

        assertEquals("en", result);
    }

    @Test
    void testGetLanguageSelectionPrompt() {
        String prompt = gracefulDegradationService.getLanguageSelectionPrompt();

        assertNotNull(prompt);
        assertTrue(prompt.contains("Hausa"));
        assertTrue(prompt.contains("Yoruba"));
        assertTrue(prompt.contains("Igbo"));
        assertTrue(prompt.contains("Fulfulde"));
        assertTrue(prompt.contains("English"));
    }

    @Test
    void testIsCriticalFailure_Bedrock() {
        assertTrue(gracefulDegradationService.isCriticalFailure("Bedrock"));
    }

    @Test
    void testIsCriticalFailure_Transcribe() {
        assertTrue(gracefulDegradationService.isCriticalFailure("Transcribe"));
    }

    @Test
    void testIsCriticalFailure_Polly() {
        assertFalse(gracefulDegradationService.isCriticalFailure("Polly"));
    }

    @Test
    void testIsCriticalFailure_Other() {
        assertFalse(gracefulDegradationService.isCriticalFailure("S3"));
        assertFalse(gracefulDegradationService.isCriticalFailure("DynamoDB"));
    }

    @Test
    void testGracefulDegradation_BedrockWithHealthCategory_Hausa() {
        when(cacheManagerService.getCachedResponse(anyString(), anyString()))
                .thenReturn(Optional.empty());

        String result = gracefulDegradationService.handleBedrockUnavailable(
                "Ina zan sami magani?", "ha", "Health");

        assertNotNull(result);
        assertTrue(result.contains("lafiya") || result.contains("cellal"));
    }

    @Test
    void testGracefulDegradation_BedrockWithEmergencyCategory_Yoruba() {
        when(cacheManagerService.getCachedResponse(anyString(), anyString()))
                .thenReturn(Optional.empty());

        String result = gracefulDegradationService.handleBedrockUnavailable(
                "Bawo ni mo ṣe le pe awọn ologun?", "yo", "Emergency");

        assertNotNull(result);
        assertTrue(result.contains("pajawiri") || result.contains("emergency"));
    }

    @Test
    void testGracefulDegradation_BedrockWithEducationCategory_Igbo() {
        when(cacheManagerService.getCachedResponse(anyString(), anyString()))
                .thenReturn(Optional.empty());

        String result = gracefulDegradationService.handleBedrockUnavailable(
                "Kedu ka m ga-esi debanye aha n'ụlọ akwụkwọ?", "ig", "Education");

        assertNotNull(result);
        assertTrue(result.contains("agụmakwụkwọ") || result.contains("education"));
    }
}

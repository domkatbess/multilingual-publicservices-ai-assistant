package com.africanservices.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class LanguageDetectionServiceTest {

    @Mock
    private BedrockService bedrockService;

    private LanguageDetectionService languageDetectionService;

    @BeforeEach
    void setUp() {
        languageDetectionService = new LanguageDetectionService(bedrockService);
    }

    @Test
    void testDetectLanguage_Hausa() throws BedrockService.BedrockException {
        // Arrange
        String text = "Sannu, yaya kuke?";
        when(bedrockService.invokeModel(anyString())).thenReturn("ha");

        // Act
        String result = languageDetectionService.detectLanguage(text);

        // Assert
        assertEquals("ha", result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testDetectLanguage_Yoruba() throws BedrockService.BedrockException {
        // Arrange
        String text = "Bawo ni, se daadaa ni?";
        when(bedrockService.invokeModel(anyString())).thenReturn("yo");

        // Act
        String result = languageDetectionService.detectLanguage(text);

        // Assert
        assertEquals("yo", result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testDetectLanguage_Igbo() throws BedrockService.BedrockException {
        // Arrange
        String text = "Kedu, ị dị mma?";
        when(bedrockService.invokeModel(anyString())).thenReturn("ig");

        // Act
        String result = languageDetectionService.detectLanguage(text);

        // Assert
        assertEquals("ig", result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testDetectLanguage_Fulfulde() throws BedrockService.BedrockException {
        // Arrange
        String text = "A jaraama, no mbadda?";
        when(bedrockService.invokeModel(anyString())).thenReturn("ff");

        // Act
        String result = languageDetectionService.detectLanguage(text);

        // Assert
        assertEquals("ff", result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testDetectLanguage_English() throws BedrockService.BedrockException {
        // Arrange
        String text = "Hello, how are you?";
        when(bedrockService.invokeModel(anyString())).thenReturn("en");

        // Act
        String result = languageDetectionService.detectLanguage(text);

        // Assert
        assertEquals("en", result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testDetectLanguage_UnsupportedLanguage_DefaultsToEnglish() throws BedrockService.BedrockException {
        // Arrange
        String text = "Bonjour, comment allez-vous?";
        when(bedrockService.invokeModel(anyString())).thenReturn("fr");

        // Act
        String result = languageDetectionService.detectLanguage(text);

        // Assert
        assertEquals("en", result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testDetectLanguage_EmptyText_DefaultsToEnglish() throws BedrockService.BedrockException {
        // Act
        String result = languageDetectionService.detectLanguage("");

        // Assert
        assertEquals("en", result);
        verify(bedrockService, never()).invokeModel(anyString());
    }

    @Test
    void testDetectLanguage_NullText_DefaultsToEnglish() throws BedrockService.BedrockException {
        // Act
        String result = languageDetectionService.detectLanguage(null);

        // Assert
        assertEquals("en", result);
        verify(bedrockService, never()).invokeModel(anyString());
    }

    @Test
    void testDetectLanguage_BedrockFailure_DefaultsToEnglish() throws BedrockService.BedrockException {
        // Arrange
        String text = "Some text";
        doThrow(new BedrockService.BedrockException("Service unavailable"))
                .when(bedrockService).invokeModel(anyString());

        // Act
        String result = languageDetectionService.detectLanguage(text);

        // Assert
        assertEquals("en", result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testDetectLanguage_ResponseWithExtraText() throws BedrockService.BedrockException {
        // Arrange
        String text = "Test text";
        when(bedrockService.invokeModel(anyString())).thenReturn("The language is ha (Hausa)");

        // Act
        String result = languageDetectionService.detectLanguage(text);

        // Assert
        assertEquals("ha", result);
    }

    @Test
    void testDetectLanguage_ResponseWithUpperCase() throws BedrockService.BedrockException {
        // Arrange
        String text = "Test text";
        when(bedrockService.invokeModel(anyString())).thenReturn("YO");

        // Act
        String result = languageDetectionService.detectLanguage(text);

        // Assert
        assertEquals("yo", result);
    }

    @Test
    void testIsSupportedLanguage_Hausa() {
        assertTrue(languageDetectionService.isSupportedLanguage("ha"));
    }

    @Test
    void testIsSupportedLanguage_Yoruba() {
        assertTrue(languageDetectionService.isSupportedLanguage("yo"));
    }

    @Test
    void testIsSupportedLanguage_Igbo() {
        assertTrue(languageDetectionService.isSupportedLanguage("ig"));
    }

    @Test
    void testIsSupportedLanguage_Fulfulde() {
        assertTrue(languageDetectionService.isSupportedLanguage("ff"));
    }

    @Test
    void testIsSupportedLanguage_English() {
        assertTrue(languageDetectionService.isSupportedLanguage("en"));
    }

    @Test
    void testIsSupportedLanguage_Unsupported() {
        assertFalse(languageDetectionService.isSupportedLanguage("fr"));
        assertFalse(languageDetectionService.isSupportedLanguage("es"));
        assertFalse(languageDetectionService.isSupportedLanguage("de"));
    }

    @Test
    void testIsSupportedLanguage_Null() {
        assertFalse(languageDetectionService.isSupportedLanguage(null));
    }

    @Test
    void testIsSupportedLanguage_CaseInsensitive() {
        assertTrue(languageDetectionService.isSupportedLanguage("HA"));
        assertTrue(languageDetectionService.isSupportedLanguage("Yo"));
        assertTrue(languageDetectionService.isSupportedLanguage("IG"));
    }

    @Test
    void testGetSupportedLanguages() {
        // Act
        Set<String> supportedLanguages = languageDetectionService.getSupportedLanguages();

        // Assert
        assertEquals(5, supportedLanguages.size());
        assertTrue(supportedLanguages.contains("ha"));
        assertTrue(supportedLanguages.contains("yo"));
        assertTrue(supportedLanguages.contains("ig"));
        assertTrue(supportedLanguages.contains("ff"));
        assertTrue(supportedLanguages.contains("en"));
    }
}

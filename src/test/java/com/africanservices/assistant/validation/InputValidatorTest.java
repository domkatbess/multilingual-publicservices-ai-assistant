package com.africanservices.assistant.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InputValidator.
 * Tests validation and sanitization logic for text and audio inputs.
 */
class InputValidatorTest {

    private InputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InputValidator();
    }

    // Text Validation Tests

    @Test
    @DisplayName("Should accept valid text input")
    void testValidTextInput() {
        String validText = "Hello, how can I access health services?";
        InputValidator.ValidationResult result = validator.validateText(validText);
        
        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should reject null text input")
    void testNullTextInput() {
        InputValidator.ValidationResult result = validator.validateText(null);
        
        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("cannot be empty"));
    }

    @Test
    @DisplayName("Should reject empty text input")
    void testEmptyTextInput() {
        InputValidator.ValidationResult result = validator.validateText("");
        
        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("cannot be empty"));
    }

    @Test
    @DisplayName("Should reject whitespace-only text input")
    void testWhitespaceOnlyTextInput() {
        InputValidator.ValidationResult result = validator.validateText("   ");
        
        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("cannot be empty"));
    }

    @Test
    @DisplayName("Should accept text with exactly 999 characters")
    void testTextWith999Characters() {
        String text = "a".repeat(999);
        InputValidator.ValidationResult result = validator.validateText(text);
        
        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should reject text with exactly 1000 characters")
    void testTextWith1000Characters() {
        String text = "a".repeat(1000);
        InputValidator.ValidationResult result = validator.validateText(text);
        
        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("fewer than 1000 characters"));
    }

    @Test
    @DisplayName("Should reject text with more than 1000 characters")
    void testTextWithMoreThan1000Characters() {
        String text = "a".repeat(1500);
        InputValidator.ValidationResult result = validator.validateText(text);
        
        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("fewer than 1000 characters"));
    }

    // Audio Format Validation Tests

    @ParameterizedTest
    @ValueSource(strings = {"wav", "mp3", "m4a", "WAV", "MP3", "M4A", "WaV", "Mp3", "M4a"})
    @DisplayName("Should accept valid audio formats (case-insensitive)")
    void testValidAudioFormats(String format) {
        InputValidator.ValidationResult result = validator.validateAudioFormat(format);
        
        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ogg", "flac", "aac", "wma", "mp4", "avi", "txt", "pdf"})
    @DisplayName("Should reject invalid audio formats")
    void testInvalidAudioFormats(String format) {
        InputValidator.ValidationResult result = validator.validateAudioFormat(format);
        
        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("WAV, MP3, or M4A"));
    }

    @Test
    @DisplayName("Should reject null audio format")
    void testNullAudioFormat() {
        InputValidator.ValidationResult result = validator.validateAudioFormat(null);
        
        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("cannot be empty"));
    }

    @Test
    @DisplayName("Should reject empty audio format")
    void testEmptyAudioFormat() {
        InputValidator.ValidationResult result = validator.validateAudioFormat("");
        
        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("cannot be empty"));
    }

    @Test
    @DisplayName("Should accept audio format with whitespace")
    void testAudioFormatWithWhitespace() {
        InputValidator.ValidationResult result = validator.validateAudioFormat("  mp3  ");
        
        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    // Input Sanitization Tests

    @Test
    @DisplayName("Should remove script tags from input")
    void testRemoveScriptTags() {
        String maliciousInput = "Hello <script>alert('XSS')</script> world";
        String sanitized = validator.sanitizeInput(maliciousInput);
        
        assertFalse(sanitized.contains("<script>"));
        assertFalse(sanitized.contains("</script>"));
        assertFalse(sanitized.contains("alert"));
        assertEquals("Hello  world", sanitized);
    }

    @Test
    @DisplayName("Should remove HTML tags from input")
    void testRemoveHtmlTags() {
        String htmlInput = "Hello <b>bold</b> and <i>italic</i> text";
        String sanitized = validator.sanitizeInput(htmlInput);
        
        assertFalse(sanitized.contains("<b>"));
        assertFalse(sanitized.contains("</b>"));
        assertFalse(sanitized.contains("<i>"));
        assertFalse(sanitized.contains("</i>"));
        assertEquals("Hello bold and italic text", sanitized);
    }

    @Test
    @DisplayName("Should remove SQL injection patterns")
    void testRemoveSqlInjectionPatterns() {
        String sqlInput = "Hello'; DROP TABLE users; --";
        String sanitized = validator.sanitizeInput(sqlInput);
        
        // The pattern should remove the SQL injection attempt
        assertFalse(sanitized.contains("'; DROP"));
        assertTrue(sanitized.contains("Hello"));
    }

    @Test
    @DisplayName("Should remove command injection characters")
    void testRemoveCommandInjectionCharacters() {
        String commandInput = "Hello; ls -la && rm -rf /";
        String sanitized = validator.sanitizeInput(commandInput);
        
        assertFalse(sanitized.contains(";"));
        assertFalse(sanitized.contains("&&"));
        assertFalse(sanitized.contains("|"));
    }

    @Test
    @DisplayName("Should handle null input in sanitization")
    void testSanitizeNullInput() {
        String sanitized = validator.sanitizeInput(null);
        
        assertNull(sanitized);
    }

    @Test
    @DisplayName("Should preserve clean text during sanitization")
    void testSanitizeCleanText() {
        String cleanInput = "How can I access government services in Hausa?";
        String sanitized = validator.sanitizeInput(cleanInput);
        
        assertEquals(cleanInput, sanitized);
    }

    @Test
    @DisplayName("Should handle multiple harmful patterns in one input")
    void testSanitizeMultiplePatterns() {
        String maliciousInput = "<script>alert('XSS')</script>Hello'; DROP TABLE users; -- <b>test</b>";
        String sanitized = validator.sanitizeInput(maliciousInput);
        
        assertFalse(sanitized.contains("<script>"));
        assertFalse(sanitized.contains("<b>"));
        assertFalse(sanitized.contains("'; DROP"));
        assertTrue(sanitized.contains("Hello"));
    }

    // Combined Validation and Sanitization Tests

    @Test
    @DisplayName("Should validate and sanitize valid input")
    void testValidateAndSanitizeValidInput() {
        String input = "How can I access health services?";
        InputValidator.SanitizedResult result = validator.validateAndSanitize(input);
        
        assertTrue(result.isValid());
        assertEquals(input, result.sanitizedText());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should validate and sanitize input with harmful content")
    void testValidateAndSanitizeWithHarmfulContent() {
        String input = "Hello <script>alert('XSS')</script> world";
        InputValidator.SanitizedResult result = validator.validateAndSanitize(input);
        
        assertTrue(result.isValid());
        assertNotNull(result.sanitizedText());
        assertFalse(result.sanitizedText().contains("<script>"));
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should fail validation for empty input in validateAndSanitize")
    void testValidateAndSanitizeEmptyInput() {
        InputValidator.SanitizedResult result = validator.validateAndSanitize("");
        
        assertFalse(result.isValid());
        assertNull(result.sanitizedText());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should fail validation for too long input in validateAndSanitize")
    void testValidateAndSanitizeTooLongInput() {
        String input = "a".repeat(1000);
        InputValidator.SanitizedResult result = validator.validateAndSanitize(input);
        
        assertFalse(result.isValid());
        assertNull(result.sanitizedText());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().get(0).contains("fewer than 1000 characters"));
    }

    @Test
    @DisplayName("Should handle edge case with exactly 999 characters and harmful content")
    void testValidateAndSanitizeEdgeCase() {
        // Create input that's under 1000 chars before sanitization
        String input = "a".repeat(980) + "<b>test</b>";
        InputValidator.SanitizedResult result = validator.validateAndSanitize(input);
        
        assertTrue(result.isValid());
        assertNotNull(result.sanitizedText());
        assertFalse(result.sanitizedText().contains("<b>"));
    }
}

package com.africanservices.assistant.validation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for validating and sanitizing user input.
 * Provides methods for text validation, audio format validation, and input sanitization
 * to protect against harmful content patterns.
 * 
 * <p>This validator implements requirements 1.2, 2.1, and 10.2 from the specification:
 * <ul>
 *   <li>Text query validation - message must not be empty and contain fewer than 1000 characters</li>
 *   <li>Audio format validation - accept WAV, MP3, or M4A format only</li>
 *   <li>Input sanitization - sanitize text input to remove potentially harmful content</li>
 * </ul>
 */
@Component
public class InputValidator {

    private static final int MAX_TEXT_LENGTH = 1000;
    private static final Pattern AUDIO_FORMAT_PATTERN = Pattern.compile("^(?i)(wav|mp3|m4a)$");
    
    // Patterns for detecting harmful content
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("(?i)('\\s*;\\s*(union|select|insert|update|delete|drop|create|alter|exec|execute)\\s+)|(--\\s*$)");
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile("[;&|`${}\\[\\]]");
    
    /**
     * Validates text input according to specification requirements.
     * 
     * @param text the text to validate
     * @return ValidationResult containing validation status and error messages
     */
    public ValidationResult validateText(String text) {
        List<String> errors = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            errors.add("Text input cannot be empty");
        } else if (text.length() >= MAX_TEXT_LENGTH) {
            errors.add("Text input must contain fewer than 1000 characters");
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * Validates audio format according to specification requirements.
     * Accepts only WAV, MP3, or M4A formats (case-insensitive).
     * 
     * @param format the audio format to validate
     * @return ValidationResult containing validation status and error messages
     */
    public ValidationResult validateAudioFormat(String format) {
        List<String> errors = new ArrayList<>();
        
        if (format == null || format.trim().isEmpty()) {
            errors.add("Audio format cannot be empty");
        } else if (!AUDIO_FORMAT_PATTERN.matcher(format.trim()).matches()) {
            errors.add("Audio format must be WAV, MP3, or M4A");
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * Sanitizes text input by removing potentially harmful content patterns.
     * Removes or escapes:
     * <ul>
     *   <li>Script tags and their content</li>
     *   <li>HTML tags</li>
     *   <li>SQL injection patterns</li>
     *   <li>Command injection characters</li>
     * </ul>
     * 
     * @param text the text to sanitize
     * @return sanitized text with harmful patterns removed
     */
    public String sanitizeInput(String text) {
        if (text == null) {
            return null;
        }
        
        String sanitized = text;
        
        // Remove script tags and their content
        sanitized = SCRIPT_TAG_PATTERN.matcher(sanitized).replaceAll("");
        
        // Remove HTML tags
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");
        
        // Remove SQL injection patterns
        sanitized = SQL_INJECTION_PATTERN.matcher(sanitized).replaceAll("");
        
        // Remove command injection characters
        sanitized = COMMAND_INJECTION_PATTERN.matcher(sanitized).replaceAll("");
        
        return sanitized.trim();
    }
    
    /**
     * Validates and sanitizes text input in a single operation.
     * First validates the text, then sanitizes it if validation passes.
     * 
     * @param text the text to validate and sanitize
     * @return SanitizedResult containing validation status, sanitized text, and error messages
     */
    public SanitizedResult validateAndSanitize(String text) {
        ValidationResult validationResult = validateText(text);
        
        if (!validationResult.isValid()) {
            return new SanitizedResult(false, null, validationResult.errors());
        }
        
        String sanitized = sanitizeInput(text);
        return new SanitizedResult(true, sanitized, List.of());
    }
    
    /**
     * Result of a validation operation.
     * 
     * @param isValid true if validation passed, false otherwise
     * @param errors list of validation error messages (empty if valid)
     */
    public record ValidationResult(boolean isValid, List<String> errors) {
    }
    
    /**
     * Result of a validation and sanitization operation.
     * 
     * @param isValid true if validation passed, false otherwise
     * @param sanitizedText the sanitized text (null if validation failed)
     * @param errors list of validation error messages (empty if valid)
     */
    public record SanitizedResult(boolean isValid, String sanitizedText, List<String> errors) {
    }
}

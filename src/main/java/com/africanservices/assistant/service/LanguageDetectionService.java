package com.africanservices.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for detecting the language of text input.
 * Uses Amazon Bedrock to identify if text is in one of the supported African languages.
 */
@Service
public class LanguageDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(LanguageDetectionService.class);
    
    // Supported language codes
    private static final Set<String> SUPPORTED_LANGUAGES = new HashSet<>(Arrays.asList(
            "ha", // Hausa
            "yo", // Yoruba
            "ig", // Igbo
            "ff", // Fulfulde
            "en"  // English
    ));
    
    private static final String DEFAULT_LANGUAGE = "en";
    
    private final BedrockService bedrockService;
    private final GracefulDegradationService gracefulDegradationService;

    @Autowired
    public LanguageDetectionService(BedrockService bedrockService, 
                                   GracefulDegradationService gracefulDegradationService) {
        this.bedrockService = bedrockService;
        this.gracefulDegradationService = gracefulDegradationService;
    }

    /**
     * Detects the language of the given text.
     * Returns a two-letter ISO 639-1 language code.
     *
     * @param text The text to analyze
     * @return Language code (ha, yo, ig, ff, or en)
     */
    public String detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Empty text provided for language detection, defaulting to English");
            return DEFAULT_LANGUAGE;
        }

        try {
            logger.debug("Detecting language for text: {}", text.substring(0, Math.min(50, text.length())));
            
            String prompt = buildLanguageDetectionPrompt(text);
            String response = bedrockService.invokeModel(prompt);
            
            String detectedLanguage = parseLanguageFromResponse(response);
            
            if (isSupportedLanguage(detectedLanguage)) {
                logger.info("Detected language: {}", detectedLanguage);
                return detectedLanguage;
            } else {
                logger.warn("Detected unsupported language: {}, defaulting to English", detectedLanguage);
                return DEFAULT_LANGUAGE;
            }
            
        } catch (Exception e) {
            logger.error("Language detection failed: {}", e.getMessage(), e);
            // Requirement 9.4: Language detection failure handler (default to English)
            return gracefulDegradationService.handleLanguageDetectionFailure();
        }
    }

    /**
     * Checks if a language code is one of the supported languages.
     *
     * @param languageCode The language code to check
     * @return true if supported, false otherwise
     */
    public boolean isSupportedLanguage(String languageCode) {
        return languageCode != null && SUPPORTED_LANGUAGES.contains(languageCode.toLowerCase());
    }

    /**
     * Gets the set of supported language codes.
     *
     * @return Set of supported language codes
     */
    public Set<String> getSupportedLanguages() {
        return new HashSet<>(SUPPORTED_LANGUAGES);
    }

    /**
     * Builds the prompt for language detection.
     */
    private String buildLanguageDetectionPrompt(String text) {
        return String.format(
                "Identify the language of the following text. " +
                "Respond with ONLY the two-letter ISO 639-1 language code. " +
                "The supported languages are: Hausa (ha), Yoruba (yo), Igbo (ig), Fulfulde (ff), and English (en). " +
                "If the language is not one of these, respond with 'en'. " +
                "Do not provide any explanation, just the language code.\n\n" +
                "Text: \"%s\"\n\n" +
                "Language code:",
                text.trim()
        );
    }

    /**
     * Parses the language code from Bedrock's response.
     * Extracts a two-letter code from the response text.
     */
    private String parseLanguageFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return DEFAULT_LANGUAGE;
        }

        // Clean up the response
        String cleaned = response.trim().toLowerCase();
        
        // Try to extract a two-letter code
        Pattern pattern = Pattern.compile("\\b([a-z]{2})\\b");
        Matcher matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
            String code = matcher.group(1);
            if (SUPPORTED_LANGUAGES.contains(code)) {
                return code;
            }
        }
        
        // Check if response contains any supported language code
        for (String lang : SUPPORTED_LANGUAGES) {
            if (cleaned.contains(lang)) {
                return lang;
            }
        }
        
        return DEFAULT_LANGUAGE;
    }
}

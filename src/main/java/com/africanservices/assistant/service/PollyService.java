package com.africanservices.assistant.service;

import com.africanservices.assistant.util.StructuredLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Polly client wrapper service for text-to-speech operations.
 * Handles voice selection based on language, neural engine configuration,
 * and cost optimization for high-volume requests.
 * 
 * Validates Requirements: 5.1, 5.2, 5.5, 11.5
 */
@Service
public class PollyService {

    private static final StructuredLogger logger = new StructuredLogger(PollyService.class);
    private static final int HIGH_VOLUME_THRESHOLD = 100; // requests per hour
    
    private final PollyClient pollyClient;
    private final Map<String, String> languageToVoiceMap;
    private final Map<String, Boolean> neuralEngineSupport;
    private int requestCount = 0;
    private long lastResetTime = System.currentTimeMillis();

    @Autowired
    public PollyService(PollyClient pollyClient) {
        this.pollyClient = pollyClient;
        this.languageToVoiceMap = initializeVoiceMapping();
        this.neuralEngineSupport = initializeNeuralSupport();
    }

    /**
     * Initialize voice mapping for supported languages.
     * Maps language codes to appropriate Polly voice IDs.
     */
    private Map<String, String> initializeVoiceMapping() {
        Map<String, String> mapping = new HashMap<>();
        // African languages - using closest available voices
        mapping.put("ha", "Zeina"); // Hausa - using Arabic voice (closest available)
        mapping.put("yo", "Joanna"); // Yoruba - using English voice
        mapping.put("ig", "Joanna"); // Igbo - using English voice
        mapping.put("ff", "Zeina"); // Fulfulde - using Arabic voice
        mapping.put("en", "Joanna"); // English - US English voice
        return mapping;
    }

    /**
     * Initialize neural engine support mapping.
     * Identifies which voices support neural engine.
     */
    private Map<String, Boolean> initializeNeuralSupport() {
        Map<String, Boolean> support = new HashMap<>();
        support.put("Zeina", true);  // Arabic voice supports neural
        support.put("Joanna", true); // English voice supports neural
        return support;
    }

    /**
     * Convert text to speech using Polly.
     * Selects appropriate voice based on language and applies cost optimization.
     * 
     * @param text Text to convert to speech
     * @param language Language code (ha, yo, ig, ff, en)
     * @return Audio data as byte array, or null if conversion fails
     */
    public byte[] synthesizeSpeech(String text, String language) {
        try {
            String voiceId = selectVoice(language);
            Engine engine = selectEngine(voiceId);
            
            logger.debug("Synthesizing speech: language={}, voice={}, engine={}", 
                        language, voiceId, engine);
            
            SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
                    .text(text)
                    .voiceId(voiceId)
                    .engine(engine)
                    .outputFormat(OutputFormat.MP3)
                    .build();

            ResponseInputStream<SynthesizeSpeechResponse> response = 
                    pollyClient.synthesizeSpeech(request);
            
            byte[] audioData = readAudioStream(response);
            
            incrementRequestCount();
            
            logger.info("Successfully synthesized speech: language={}, size={} bytes", 
                       language, audioData.length);
            
            return audioData;
            
        } catch (PollyException e) {
            logger.warn("Polly synthesis failed: language={}, error={}", 
                       language, e.getMessage());
            return null;
        } catch (IOException e) {
            logger.warn("Failed to read audio stream: language={}, error={}", 
                       language, e.getMessage());
            return null;
        }
    }

    /**
     * Select appropriate Polly voice based on language.
     * 
     * @param language Language code
     * @return Polly voice ID
     */
    private String selectVoice(String language) {
        String voice = languageToVoiceMap.getOrDefault(language, "Joanna");
        logger.debug("Selected voice {} for language {}", voice, language);
        return voice;
    }

    /**
     * Select engine (neural or standard) based on voice support and cost optimization.
     * Uses standard engine for high-volume requests to reduce costs.
     * 
     * @param voiceId Polly voice ID
     * @return Engine type (NEURAL or STANDARD)
     */
    private Engine selectEngine(String voiceId) {
        // Check if high volume - use standard for cost optimization
        if (isHighVolume()) {
            logger.debug("High volume detected, using standard engine for cost optimization");
            return Engine.STANDARD;
        }
        
        // Use neural if supported
        if (neuralEngineSupport.getOrDefault(voiceId, false)) {
            logger.debug("Using neural engine for voice {}", voiceId);
            return Engine.NEURAL;
        }
        
        logger.debug("Using standard engine for voice {}", voiceId);
        return Engine.STANDARD;
    }

    /**
     * Check if current request volume is high.
     * Resets counter every hour.
     * 
     * @return true if high volume, false otherwise
     */
    private boolean isHighVolume() {
        long currentTime = System.currentTimeMillis();
        long hourInMillis = 60 * 60 * 1000;
        
        // Reset counter if more than an hour has passed
        if (currentTime - lastResetTime > hourInMillis) {
            requestCount = 0;
            lastResetTime = currentTime;
        }
        
        return requestCount >= HIGH_VOLUME_THRESHOLD;
    }

    /**
     * Increment request count for volume tracking.
     */
    private void incrementRequestCount() {
        requestCount++;
    }

    /**
     * Read audio stream from Polly response into byte array.
     * 
     * @param responseStream Response stream from Polly
     * @return Audio data as byte array
     * @throws IOException if stream reading fails
     */
    private byte[] readAudioStream(ResponseInputStream<SynthesizeSpeechResponse> responseStream) 
            throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            
            while ((bytesRead = responseStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            return outputStream.toByteArray();
        } finally {
            responseStream.close();
        }
    }

    /**
     * Get the voice ID for a given language.
     * Useful for testing and validation.
     * 
     * @param language Language code
     * @return Polly voice ID
     */
    public String getVoiceForLanguage(String language) {
        return languageToVoiceMap.getOrDefault(language, "Joanna");
    }

    /**
     * Check if a voice supports neural engine.
     * 
     * @param voiceId Polly voice ID
     * @return true if neural engine is supported, false otherwise
     */
    public boolean supportsNeuralEngine(String voiceId) {
        return neuralEngineSupport.getOrDefault(voiceId, false);
    }
}

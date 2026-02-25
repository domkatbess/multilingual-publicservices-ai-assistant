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
import com.africanservices.assistant.util.StructuredLogger;
import com.africanservices.assistant.validation.InputValidator;
import com.africanservices.assistant.validation.InputValidator.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Lambda function handler for processing voice-based user queries.
 * Handles audio input, transcription, and forwards to text processing logic.
 * 
 * Validates Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 9.3, 12.1, 12.2, 12.3, 12.4
 */
@Component("audioHandlerFunction")
public class AudioHandlerFunction implements Function<AudioRequest, Object> {

    private static final StructuredLogger logger = new StructuredLogger(AudioHandlerFunction.class);
    
    private final InputValidator inputValidator;
    private final S3Service s3Service;
    private final TranscribeService transcribeService;
    private final LanguageDetectionService languageDetectionService;
    private final ServiceCategoryClassifier serviceCategoryClassifier;
    private final SessionManagerService sessionManagerService;
    private final CacheManagerService cacheManagerService;
    private final ResponseGeneratorService responseGeneratorService;
    private final TextToSpeechService textToSpeechService;
    private final MetricsEmitter metricsEmitter;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    @Autowired
    public AudioHandlerFunction(
            InputValidator inputValidator,
            S3Service s3Service,
            TranscribeService transcribeService,
            LanguageDetectionService languageDetectionService,
            ServiceCategoryClassifier serviceCategoryClassifier,
            SessionManagerService sessionManagerService,
            CacheManagerService cacheManagerService,
            ResponseGeneratorService responseGeneratorService,
            TextToSpeechService textToSpeechService,
            MetricsEmitter metricsEmitter,
            Validator validator,
            ObjectMapper objectMapper) {
        this.inputValidator = inputValidator;
        this.s3Service = s3Service;
        this.transcribeService = transcribeService;
        this.languageDetectionService = languageDetectionService;
        this.serviceCategoryClassifier = serviceCategoryClassifier;
        this.sessionManagerService = sessionManagerService;
        this.cacheManagerService = cacheManagerService;
        this.responseGeneratorService = responseGeneratorService;
        this.textToSpeechService = textToSpeechService;
        this.metricsEmitter = metricsEmitter;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public Object apply(AudioRequest request) {
        long startTime = System.currentTimeMillis();
        String correlationId = StructuredLogger.generateCorrelationId();
        StructuredLogger.setCorrelationId(correlationId);
        
        try {
            // Log incoming request
            logger.logRequest(correlationId, request.userId(), request.sessionId(), 
                    "Audio request received");
            
            // Validate request
            Set<ConstraintViolation<AudioRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                String errorMessage = violations.iterator().next().getMessage();
                return buildErrorResponse("VALIDATION_ERROR", errorMessage, 
                        "Invalid audio request", false);
            }
            
            // Validate audio format
            ValidationResult formatValidation = inputValidator.validateAudioFormat(request.audioFormat());
            if (!formatValidation.isValid()) {
                String errorMessage = String.join(", ", formatValidation.errors());
                return buildErrorResponse("INVALID_AUDIO_FORMAT", errorMessage,
                        "Audio format must be WAV, MP3, or M4A", false);
            }
            
            // Decode base64 audio data
            byte[] audioData;
            try {
                audioData = Base64.getDecoder().decode(request.audioData());
            } catch (IllegalArgumentException e) {
                logger.warn("Failed to decode base64 audio data: {}", e.getMessage());
                return buildErrorResponse("INVALID_AUDIO_DATA", 
                        "Audio data must be valid base64 encoded",
                        "Invalid audio data encoding", false);
            }
            
            // Store audio file in S3 with unique identifier
            String fileId = s3Service.uploadAudioFile(audioData, request.audioFormat(), request.userId());
            if (fileId == null) {
                logger.warn("Failed to upload audio file to S3");
                return buildErrorResponse("STORAGE_ERROR", 
                        "Failed to store audio file",
                        "Audio storage failed", true);
            }
            
            logger.info("Audio file uploaded: fileId={}, size={} bytes", fileId, audioData.length);
            
            // Invoke Transcribe for speech-to-text
            String languageCode = determineTranscribeLanguageCode(request.sessionId());
            String transcribedText = transcribeService.transcribeAudio(fileId, request.audioFormat(), languageCode);
            
            // Handle transcription failure
            if (transcribedText == null || transcribedText.trim().isEmpty()) {
                logger.warn("Transcription failed or returned empty text");
                // Clean up S3 file
                s3Service.deleteAudioFile(fileId);
                
                metricsEmitter.emitMetric("TranscriptionFailure", 1.0, StandardUnit.COUNT);
                return buildErrorResponse("TRANSCRIPTION_FAILED",
                        "Failed to transcribe audio. Please try typing your question instead.",
                        "Speech-to-text conversion failed", true);
            }
            
            logger.info("Transcription completed: textLength={}", transcribedText.length());
            
            // Forward transcribed text to InputHandlerFunction logic
            SuccessResponse response = processTextQuery(
                    request.userId(),
                    request.sessionId(),
                    transcribedText,
                    correlationId
            );
            
            // Emit metrics
            long duration = System.currentTimeMillis() - startTime;
            metricsEmitter.emitMetric("AudioRequestLatency", duration, StandardUnit.MILLISECONDS);
            metricsEmitter.emitMetric("AudioRequestCount", 1.0, StandardUnit.COUNT);
            
            logger.logResponse(correlationId, 200, "Audio request processed successfully", duration);
            
            return response;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.logError(correlationId, "INTERNAL_ERROR", 
                    "Unexpected error processing audio request", e);
            
            metricsEmitter.emitMetric("AudioRequestError", 1.0, StandardUnit.COUNT);
            metricsEmitter.emitMetric("AudioRequestLatency", duration, StandardUnit.MILLISECONDS);
            
            return buildErrorResponse("INTERNAL_ERROR",
                    "An unexpected error occurred while processing your audio",
                    "Internal server error", true);
        } finally {
            StructuredLogger.clearContext();
        }
    }

    /**
     * Process transcribed text query using the same logic as InputHandlerFunction.
     * This method implements the core text processing workflow.
     * 
     * @param userId User identifier
     * @param sessionId Session identifier
     * @param message Transcribed text message
     * @param correlationId Correlation ID for logging
     * @return SuccessResponse with generated response
     */
    private SuccessResponse processTextQuery(String userId, String sessionId, 
                                            String message, String correlationId) {
        try {
            // Retrieve or create session
            Optional<SessionData> sessionOpt = sessionManagerService.getSession(sessionId);
            String language;
            
            if (sessionOpt.isEmpty()) {
                // Create new session
                language = languageDetectionService.detectLanguage(message);
                sessionManagerService.createSession(userId, language);
                logger.info("Created new session: sessionId={}, language={}", sessionId, language);
            } else {
                // Use existing session language
                SessionData sessionData = sessionOpt.get();
                language = sessionData.getLanguage();
                logger.debug("Using existing session: sessionId={}, language={}", sessionId, language);
            }
            
            // Detect language if not already set
            if (language == null || language.isEmpty()) {
                language = languageDetectionService.detectLanguage(message);
                sessionManagerService.updateSessionLanguage(sessionId, language);
            }
            
            // Classify service category
            ServiceCategory category = serviceCategoryClassifier.classifyQuery(message);
            logger.info("Query classified: category={}, language={}", category, language);
            
            // Check cache for existing response
            Optional<String> cachedResponse = cacheManagerService.getCachedResponse(message, language);
            String responseText;
            
            if (cachedResponse.isPresent()) {
                responseText = cachedResponse.get();
                logger.info("Cache hit for query");
                metricsEmitter.emitCacheHit(language);
            } else {
                // Generate response if cache miss
                try {
                    responseText = responseGeneratorService.generateResponse(
                            sessionId, message, language, category);
                    
                    // Store response in cache
                    cacheManagerService.cacheResponse(message, language, responseText);
                    metricsEmitter.emitCacheMiss(language);
                    
                } catch (ResponseGenerationException e) {
                    logger.warn("Response generation failed: {}", e.getMessage());
                    throw new RuntimeException("Failed to generate response", e);
                }
            }
            
            // Update session with conversation turn
            sessionManagerService.updateSession(sessionId, message, responseText);
            
            // Optionally generate audio (always generate for audio requests)
            TTSResult ttsResult = textToSpeechService.convertTextToSpeech(responseText, language, userId);
            
            // Emit metrics
            metricsEmitter.emitRequestCount(language, category.toString());
            
            // Return success response
            if (ttsResult != null) {
                return new SuccessResponse(
                        responseText,
                        language,
                        category.toString(),
                        ttsResult.getAudioUrl(),
                        ttsResult.getExpirationEpochSeconds()
                );
            } else {
                // Graceful degradation: return text-only response if TTS fails
                return new SuccessResponse(responseText, language, category.toString());
            }
            
        } catch (Exception e) {
            logger.warn("Error processing text query: {}", e.getMessage());
            throw new RuntimeException("Failed to process text query", e);
        }
    }

    /**
     * Determine the Transcribe language code based on session language preference.
     * Maps ISO 639-1 codes to Transcribe language codes.
     * 
     * @param sessionId Session identifier
     * @return Transcribe language code (e.g., "en-US", "ha-NG")
     */
    private String determineTranscribeLanguageCode(String sessionId) {
        Optional<SessionData> sessionOpt = sessionManagerService.getSession(sessionId);
        
        if (sessionOpt.isPresent()) {
            String language = sessionOpt.get().getLanguage();
            return mapToTranscribeLanguageCode(language);
        }
        
        // Default to English if no session found
        return "en-US";
    }

    /**
     * Map ISO 639-1 language code to Transcribe language code.
     * 
     * @param languageCode ISO 639-1 language code (ha, yo, ig, ff, en)
     * @return Transcribe language code
     */
    private String mapToTranscribeLanguageCode(String languageCode) {
        if (languageCode == null) {
            return "en-US";
        }
        
        return switch (languageCode.toLowerCase()) {
            case "ha" -> "ha-NG"; // Hausa (Nigeria)
            case "yo" -> "yo-NG"; // Yoruba (Nigeria) - if supported
            case "ig" -> "ig-NG"; // Igbo (Nigeria) - if supported
            case "ff" -> "ff-SN"; // Fulfulde (Senegal) - if supported
            case "en" -> "en-US"; // English (US)
            default -> "en-US"; // Default to English
        };
    }

    /**
     * Build an error response with proper structure.
     * 
     * @param errorCode Error code
     * @param message Error message in user's language
     * @param fallbackMessage Fallback message in English
     * @param retryable Whether the error is retryable
     * @return ErrorResponse object
     */
    private ErrorResponse buildErrorResponse(String errorCode, String message, 
                                            String fallbackMessage, boolean retryable) {
        return new ErrorResponse(errorCode, message, fallbackMessage, retryable);
    }
}

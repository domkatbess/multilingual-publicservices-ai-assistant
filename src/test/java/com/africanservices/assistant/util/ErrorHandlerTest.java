package com.africanservices.assistant.util;

import com.africanservices.assistant.model.ErrorResponse;
import com.africanservices.assistant.service.BedrockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorHandlerTest {

    private ErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        errorHandler = new ErrorHandler();
    }

    @Test
    void testBuildErrorResponse_WithEnglish() {
        ErrorResponse response = errorHandler.buildErrorResponse(
                ErrorHandler.ERROR_INVALID_INPUT, "en");

        assertTrue(response.error());
        assertEquals(ErrorHandler.ERROR_INVALID_INPUT, response.errorCode());
        assertNotNull(response.message());
        assertNotNull(response.fallbackMessage());
        assertFalse(response.retryable());
    }

    @Test
    void testBuildErrorResponse_WithHausa() {
        ErrorResponse response = errorHandler.buildErrorResponse(
                ErrorHandler.ERROR_BEDROCK_UNAVAILABLE, "ha");

        assertTrue(response.error());
        assertEquals(ErrorHandler.ERROR_BEDROCK_UNAVAILABLE, response.errorCode());
        assertNotNull(response.message());
        assertTrue(response.message().contains("AI")); // Hausa message should contain AI
        assertNotNull(response.fallbackMessage());
        assertTrue(response.retryable());
    }

    @Test
    void testBuildErrorResponse_WithYoruba() {
        ErrorResponse response = errorHandler.buildErrorResponse(
                ErrorHandler.ERROR_POLLY_UNAVAILABLE, "yo");

        assertTrue(response.error());
        assertEquals(ErrorHandler.ERROR_POLLY_UNAVAILABLE, response.errorCode());
        assertNotNull(response.message());
        assertNotNull(response.fallbackMessage());
        assertFalse(response.retryable()); // Polly unavailable is not retryable (graceful degradation)
    }

    @Test
    void testBuildErrorResponse_WithIgbo() {
        ErrorResponse response = errorHandler.buildErrorResponse(
                ErrorHandler.ERROR_TRANSCRIBE_FAILED, "ig");

        assertTrue(response.error());
        assertEquals(ErrorHandler.ERROR_TRANSCRIBE_FAILED, response.errorCode());
        assertNotNull(response.message());
        assertNotNull(response.fallbackMessage());
        assertTrue(response.retryable());
    }

    @Test
    void testBuildErrorResponse_WithFulfulde() {
        ErrorResponse response = errorHandler.buildErrorResponse(
                ErrorHandler.ERROR_AUDIO_FORMAT_INVALID, "ff");

        assertTrue(response.error());
        assertEquals(ErrorHandler.ERROR_AUDIO_FORMAT_INVALID, response.errorCode());
        assertNotNull(response.message());
        assertNotNull(response.fallbackMessage());
        assertFalse(response.retryable());
    }

    @Test
    void testBuildErrorResponse_WithUnsupportedLanguage_FallsBackToEnglish() {
        ErrorResponse response = errorHandler.buildErrorResponse(
                ErrorHandler.ERROR_INVALID_INPUT, "fr");

        assertTrue(response.error());
        assertEquals(ErrorHandler.ERROR_INVALID_INPUT, response.errorCode());
        assertEquals(response.message(), response.fallbackMessage()); // Should use English
    }

    @Test
    void testBuildErrorResponse_WithCustomMessage() {
        String customMessage = "Custom error message";
        ErrorResponse response = errorHandler.buildErrorResponse(
                ErrorHandler.ERROR_INTERNAL, customMessage, "en");

        assertTrue(response.error());
        assertEquals(ErrorHandler.ERROR_INTERNAL, response.errorCode());
        assertEquals(customMessage, response.message());
        assertNotNull(response.fallbackMessage());
        assertTrue(response.retryable());
    }

    @Test
    void testBuildErrorResponseFromException_BedrockException() {
        Exception exception = new BedrockService.BedrockException("Bedrock service error");
        ErrorResponse response = errorHandler.buildErrorResponseFromException(exception, "en");

        assertTrue(response.error());
        assertEquals(ErrorHandler.ERROR_BEDROCK_UNAVAILABLE, response.errorCode());
        assertTrue(response.retryable());
    }

    @Test
    void testBuildErrorResponseFromException_GenericException() {
        Exception exception = new RuntimeException("Unknown error");
        ErrorResponse response = errorHandler.buildErrorResponseFromException(exception, "en");

        assertTrue(response.error());
        assertEquals(ErrorHandler.ERROR_INTERNAL, response.errorCode());
        assertTrue(response.retryable());
    }

    @Test
    void testRetryableErrors() {
        // Retryable errors
        assertTrue(errorHandler.buildErrorResponse(ErrorHandler.ERROR_BEDROCK_UNAVAILABLE, "en").retryable());
        assertTrue(errorHandler.buildErrorResponse(ErrorHandler.ERROR_TRANSCRIBE_FAILED, "en").retryable());
        assertTrue(errorHandler.buildErrorResponse(ErrorHandler.ERROR_CACHE_FAILURE, "en").retryable());
        assertTrue(errorHandler.buildErrorResponse(ErrorHandler.ERROR_S3_FAILURE, "en").retryable());
        assertTrue(errorHandler.buildErrorResponse(ErrorHandler.ERROR_INTERNAL, "en").retryable());
        assertTrue(errorHandler.buildErrorResponse(ErrorHandler.ERROR_RATE_LIMIT, "en").retryable());
    }

    @Test
    void testNonRetryableErrors() {
        // Non-retryable errors
        assertFalse(errorHandler.buildErrorResponse(ErrorHandler.ERROR_INVALID_INPUT, "en").retryable());
        assertFalse(errorHandler.buildErrorResponse(ErrorHandler.ERROR_AUDIO_FORMAT_INVALID, "en").retryable());
        assertFalse(errorHandler.buildErrorResponse(ErrorHandler.ERROR_SESSION_NOT_FOUND, "en").retryable());
        assertFalse(errorHandler.buildErrorResponse(ErrorHandler.ERROR_AUTHENTICATION, "en").retryable());
        assertFalse(errorHandler.buildErrorResponse(ErrorHandler.ERROR_LANGUAGE_DETECTION_FAILED, "en").retryable());
    }

    @Test
    void testAllErrorCodesHaveFallbackMessages() {
        String[] errorCodes = {
            ErrorHandler.ERROR_INVALID_INPUT,
            ErrorHandler.ERROR_LANGUAGE_DETECTION_FAILED,
            ErrorHandler.ERROR_BEDROCK_UNAVAILABLE,
            ErrorHandler.ERROR_POLLY_UNAVAILABLE,
            ErrorHandler.ERROR_TRANSCRIBE_FAILED,
            ErrorHandler.ERROR_AUDIO_FORMAT_INVALID,
            ErrorHandler.ERROR_SESSION_NOT_FOUND,
            ErrorHandler.ERROR_CACHE_FAILURE,
            ErrorHandler.ERROR_S3_FAILURE,
            ErrorHandler.ERROR_INTERNAL,
            ErrorHandler.ERROR_RATE_LIMIT,
            ErrorHandler.ERROR_AUTHENTICATION
        };

        for (String errorCode : errorCodes) {
            ErrorResponse response = errorHandler.buildErrorResponse(errorCode, "en");
            assertNotNull(response.fallbackMessage(), 
                         "Error code " + errorCode + " should have a fallback message");
            assertFalse(response.fallbackMessage().isEmpty(), 
                       "Fallback message for " + errorCode + " should not be empty");
        }
    }

    @Test
    void testErrorResponseCompleteness() {
        // Property 22: Error Response Completeness
        ErrorResponse response = errorHandler.buildErrorResponse(
                ErrorHandler.ERROR_BEDROCK_UNAVAILABLE, "ha");

        // All required fields must be present
        assertTrue(response.error());
        assertNotNull(response.errorCode());
        assertFalse(response.errorCode().isEmpty());
        assertNotNull(response.message());
        assertFalse(response.message().isEmpty());
        assertNotNull(response.fallbackMessage());
        assertFalse(response.fallbackMessage().isEmpty());
        // retryable is a boolean, always has a value
    }
}

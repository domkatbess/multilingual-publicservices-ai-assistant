package com.africanservices.assistant.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/**
 * Structured logging utility with correlation ID support for CloudWatch Logs.
 * Provides JSON-formatted logging with request/response correlation identifiers.
 * 
 * Requirements: 12.1, 12.2
 */
public class StructuredLogger {
    
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_ID_KEY = "userId";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String LANGUAGE_KEY = "language";
    private static final String SERVICE_CATEGORY_KEY = "serviceCategory";
    private static final String EVENT_TYPE_KEY = "eventType";
    
    private final Logger logger;
    
    /**
     * Creates a structured logger for the specified class.
     * 
     * @param clazz The class to create the logger for
     */
    public StructuredLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Generates a new correlation ID for tracking requests across components.
     * 
     * @return A unique correlation ID
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Sets the correlation ID in the MDC context for the current thread.
     * This ID will be included in all subsequent log entries.
     * 
     * @param correlationId The correlation ID to set
     */
    public static void setCorrelationId(String correlationId) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }
    
    /**
     * Gets the current correlation ID from the MDC context.
     * 
     * @return The current correlation ID, or null if not set
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }
    
    /**
     * Clears the correlation ID from the MDC context.
     */
    public static void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_KEY);
    }
    
    /**
     * Clears all MDC context.
     */
    public static void clearContext() {
        MDC.clear();
    }
    
    /**
     * Logs an incoming request with structured context.
     * 
     * @param requestId The unique request identifier
     * @param userId The user identifier
     * @param sessionId The session identifier
     * @param message The request message or description
     */
    public void logRequest(String requestId, String userId, String sessionId, String message) {
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(USER_ID_KEY, userId);
        MDC.put(SESSION_ID_KEY, sessionId);
        MDC.put(EVENT_TYPE_KEY, "REQUEST");
        
        logger.info("Incoming request: {}", message);
        
        MDC.remove(EVENT_TYPE_KEY);
    }
    
    /**
     * Logs an incoming request with additional context.
     * 
     * @param requestId The unique request identifier
     * @param userId The user identifier
     * @param sessionId The session identifier
     * @param message The request message or description
     * @param additionalContext Additional context to include in the log
     */
    public void logRequest(String requestId, String userId, String sessionId, String message, 
                          Map<String, String> additionalContext) {
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(USER_ID_KEY, userId);
        MDC.put(SESSION_ID_KEY, sessionId);
        MDC.put(EVENT_TYPE_KEY, "REQUEST");
        
        if (additionalContext != null) {
            additionalContext.forEach(MDC::put);
        }
        
        logger.info("Incoming request: {}", message);
        
        MDC.remove(EVENT_TYPE_KEY);
        if (additionalContext != null) {
            additionalContext.keySet().forEach(MDC::remove);
        }
    }
    
    /**
     * Logs a response with structured context.
     * 
     * @param requestId The unique request identifier
     * @param statusCode The response status code
     * @param message The response message or description
     * @param durationMs The request duration in milliseconds
     */
    public void logResponse(String requestId, int statusCode, String message, long durationMs) {
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(EVENT_TYPE_KEY, "RESPONSE");
        MDC.put("statusCode", String.valueOf(statusCode));
        MDC.put("durationMs", String.valueOf(durationMs));
        
        logger.info("Response sent: {}", message);
        
        MDC.remove(EVENT_TYPE_KEY);
        MDC.remove("statusCode");
        MDC.remove("durationMs");
        MDC.remove(REQUEST_ID_KEY);
    }
    
    /**
     * Logs a response with language and service category context.
     * 
     * @param requestId The unique request identifier
     * @param statusCode The response status code
     * @param message The response message or description
     * @param durationMs The request duration in milliseconds
     * @param language The detected or preferred language
     * @param serviceCategory The service category
     */
    public void logResponse(String requestId, int statusCode, String message, long durationMs,
                           String language, String serviceCategory) {
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(EVENT_TYPE_KEY, "RESPONSE");
        MDC.put("statusCode", String.valueOf(statusCode));
        MDC.put("durationMs", String.valueOf(durationMs));
        MDC.put(LANGUAGE_KEY, language);
        MDC.put(SERVICE_CATEGORY_KEY, serviceCategory);
        
        logger.info("Response sent: {}", message);
        
        MDC.remove(EVENT_TYPE_KEY);
        MDC.remove("statusCode");
        MDC.remove("durationMs");
        MDC.remove(LANGUAGE_KEY);
        MDC.remove(SERVICE_CATEGORY_KEY);
        MDC.remove(REQUEST_ID_KEY);
    }
    
    /**
     * Logs an error with structured context.
     * 
     * @param requestId The unique request identifier
     * @param errorCode The error code
     * @param message The error message
     * @param throwable The exception that occurred
     */
    public void logError(String requestId, String errorCode, String message, Throwable throwable) {
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(EVENT_TYPE_KEY, "ERROR");
        MDC.put("errorCode", errorCode);
        
        if (throwable != null) {
            logger.error("Error occurred: {} - {}", message, throwable.getMessage(), throwable);
        } else {
            logger.error("Error occurred: {}", message);
        }
        
        MDC.remove(EVENT_TYPE_KEY);
        MDC.remove("errorCode");
        MDC.remove(REQUEST_ID_KEY);
    }
    
    /**
     * Logs an error with additional context.
     * 
     * @param requestId The unique request identifier
     * @param errorCode The error code
     * @param message The error message
     * @param throwable The exception that occurred
     * @param additionalContext Additional context to include in the log
     */
    public void logError(String requestId, String errorCode, String message, Throwable throwable,
                        Map<String, String> additionalContext) {
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(EVENT_TYPE_KEY, "ERROR");
        MDC.put("errorCode", errorCode);
        
        if (additionalContext != null) {
            additionalContext.forEach(MDC::put);
        }
        
        if (throwable != null) {
            logger.error("Error occurred: {} - {}", message, throwable.getMessage(), throwable);
        } else {
            logger.error("Error occurred: {}", message);
        }
        
        MDC.remove(EVENT_TYPE_KEY);
        MDC.remove("errorCode");
        MDC.remove(REQUEST_ID_KEY);
        if (additionalContext != null) {
            additionalContext.keySet().forEach(MDC::remove);
        }
    }
    
    /**
     * Logs a general info message with correlation context.
     * 
     * @param message The message to log
     */
    public void info(String message) {
        logger.info(message);
    }
    
    /**
     * Logs a general info message with parameters.
     * 
     * @param message The message template
     * @param args The message arguments
     */
    public void info(String message, Object... args) {
        logger.info(message, args);
    }
    
    /**
     * Logs a general debug message.
     * 
     * @param message The message to log
     */
    public void debug(String message) {
        logger.debug(message);
    }
    
    /**
     * Logs a general debug message with parameters.
     * 
     * @param message The message template
     * @param args The message arguments
     */
    public void debug(String message, Object... args) {
        logger.debug(message, args);
    }
    
    /**
     * Logs a general warning message.
     * 
     * @param message The message to log
     */
    public void warn(String message) {
        logger.warn(message);
    }
    
    /**
     * Logs a general warning message with parameters.
     * 
     * @param message The message template
     * @param args The message arguments
     */
    public void warn(String message, Object... args) {
        logger.warn(message, args);
    }
}

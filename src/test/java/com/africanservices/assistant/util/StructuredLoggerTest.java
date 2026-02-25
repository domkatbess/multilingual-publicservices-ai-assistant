package com.africanservices.assistant.util;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StructuredLogger.
 * Validates Requirements 12.1, 12.2
 */
class StructuredLoggerTest {
    
    private StructuredLogger structuredLogger;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;
    
    @BeforeEach
    void setUp() {
        structuredLogger = new StructuredLogger(StructuredLoggerTest.class);
        
        // Set up test appender to capture log events
        logger = (Logger) LoggerFactory.getLogger(StructuredLoggerTest.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        
        // Clear MDC before each test
        MDC.clear();
    }
    
    @AfterEach
    void tearDown() {
        MDC.clear();
        if (listAppender != null) {
            listAppender.stop();
        }
    }
    
    @Test
    void testGenerateCorrelationId_ReturnsUniqueIds() {
        String id1 = StructuredLogger.generateCorrelationId();
        String id2 = StructuredLogger.generateCorrelationId();
        
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertTrue(id1.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
    
    @Test
    void testSetAndGetCorrelationId() {
        String correlationId = "test-correlation-id";
        
        StructuredLogger.setCorrelationId(correlationId);
        
        assertEquals(correlationId, StructuredLogger.getCorrelationId());
        assertEquals(correlationId, MDC.get("correlationId"));
    }
    
    @Test
    void testClearCorrelationId() {
        StructuredLogger.setCorrelationId("test-id");
        assertNotNull(StructuredLogger.getCorrelationId());
        
        StructuredLogger.clearCorrelationId();
        
        assertNull(StructuredLogger.getCorrelationId());
    }
    
    @Test
    void testClearContext() {
        MDC.put("key1", "value1");
        MDC.put("key2", "value2");
        StructuredLogger.setCorrelationId("test-id");
        
        StructuredLogger.clearContext();
        
        assertNull(MDC.get("key1"));
        assertNull(MDC.get("key2"));
        assertNull(StructuredLogger.getCorrelationId());
    }
    
    @Test
    void testLogRequest_WithBasicInfo() {
        String requestId = "req-123";
        String userId = "user-456";
        String sessionId = "session-789";
        String message = "Processing text query";
        
        structuredLogger.logRequest(requestId, userId, sessionId, message);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        
        ILoggingEvent event = logsList.get(0);
        assertEquals("Incoming request: " + message, event.getFormattedMessage());
        assertEquals("INFO", event.getLevel().toString());
    }
    
    @Test
    void testLogRequest_WithAdditionalContext() {
        String requestId = "req-123";
        String userId = "user-456";
        String sessionId = "session-789";
        String message = "Processing text query";
        Map<String, String> context = new HashMap<>();
        context.put("language", "ha");
        context.put("serviceCategory", "Health");
        
        structuredLogger.logRequest(requestId, userId, sessionId, message, context);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        
        ILoggingEvent event = logsList.get(0);
        assertEquals("Incoming request: " + message, event.getFormattedMessage());
        
        // Verify context was cleaned up after logging
        assertNull(MDC.get("language"));
        assertNull(MDC.get("serviceCategory"));
    }
    
    @Test
    void testLogResponse_WithBasicInfo() {
        String requestId = "req-123";
        int statusCode = 200;
        String message = "Request processed successfully";
        long durationMs = 150;
        
        structuredLogger.logResponse(requestId, statusCode, message, durationMs);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        
        ILoggingEvent event = logsList.get(0);
        assertEquals("Response sent: " + message, event.getFormattedMessage());
        assertEquals("INFO", event.getLevel().toString());
    }
    
    @Test
    void testLogResponse_WithLanguageAndCategory() {
        String requestId = "req-123";
        int statusCode = 200;
        String message = "Request processed successfully";
        long durationMs = 150;
        String language = "yo";
        String serviceCategory = "Education";
        
        structuredLogger.logResponse(requestId, statusCode, message, durationMs, language, serviceCategory);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        
        ILoggingEvent event = logsList.get(0);
        assertEquals("Response sent: " + message, event.getFormattedMessage());
        
        // Verify context was cleaned up
        assertNull(MDC.get("language"));
        assertNull(MDC.get("serviceCategory"));
    }
    
    @Test
    void testLogError_WithException() {
        String requestId = "req-123";
        String errorCode = "BEDROCK_ERROR";
        String message = "Failed to generate response";
        Exception exception = new RuntimeException("Bedrock service unavailable");
        
        structuredLogger.logError(requestId, errorCode, message, exception);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        
        ILoggingEvent event = logsList.get(0);
        assertTrue(event.getFormattedMessage().contains("Error occurred"));
        assertTrue(event.getFormattedMessage().contains(message));
        assertEquals("ERROR", event.getLevel().toString());
        assertNotNull(event.getThrowableProxy());
    }
    
    @Test
    void testLogError_WithoutException() {
        String requestId = "req-123";
        String errorCode = "VALIDATION_ERROR";
        String message = "Invalid input format";
        
        structuredLogger.logError(requestId, errorCode, message, null);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        
        ILoggingEvent event = logsList.get(0);
        assertTrue(event.getFormattedMessage().contains("Error occurred"));
        assertTrue(event.getFormattedMessage().contains(message));
        assertEquals("ERROR", event.getLevel().toString());
        assertNull(event.getThrowableProxy());
    }
    
    @Test
    void testLogError_WithAdditionalContext() {
        String requestId = "req-123";
        String errorCode = "TRANSCRIBE_ERROR";
        String message = "Transcription failed";
        Exception exception = new RuntimeException("Audio format not supported");
        Map<String, String> context = new HashMap<>();
        context.put("audioFormat", "unknown");
        context.put("fileSize", "1024");
        
        structuredLogger.logError(requestId, errorCode, message, exception, context);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        
        ILoggingEvent event = logsList.get(0);
        assertTrue(event.getFormattedMessage().contains("Error occurred"));
        assertEquals("ERROR", event.getLevel().toString());
        
        // Verify context was cleaned up
        assertNull(MDC.get("audioFormat"));
        assertNull(MDC.get("fileSize"));
    }
    
    @Test
    void testInfo_SimpleMessage() {
        String message = "Processing started";
        
        structuredLogger.info(message);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertEquals(message, logsList.get(0).getFormattedMessage());
        assertEquals("INFO", logsList.get(0).getLevel().toString());
    }
    
    @Test
    void testInfo_WithParameters() {
        structuredLogger.info("User {} started session {}", "user-123", "session-456");
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertEquals("User user-123 started session session-456", logsList.get(0).getFormattedMessage());
    }
    
    @Test
    void testDebug_SimpleMessage() {
        // Set logger level to DEBUG for this test
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        
        String message = "Debug information";
        structuredLogger.debug(message);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertEquals(message, logsList.get(0).getFormattedMessage());
        assertEquals("DEBUG", logsList.get(0).getLevel().toString());
    }
    
    @Test
    void testWarn_SimpleMessage() {
        String message = "Warning message";
        
        structuredLogger.warn(message);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        assertEquals(message, logsList.get(0).getFormattedMessage());
        assertEquals("WARN", logsList.get(0).getLevel().toString());
    }
    
    @Test
    void testCorrelationId_PersistsAcrossMultipleLogs() {
        String correlationId = StructuredLogger.generateCorrelationId();
        StructuredLogger.setCorrelationId(correlationId);
        
        structuredLogger.info("First log");
        assertEquals(correlationId, StructuredLogger.getCorrelationId());
        
        structuredLogger.info("Second log");
        assertEquals(correlationId, StructuredLogger.getCorrelationId());
        
        structuredLogger.info("Third log");
        assertEquals(correlationId, StructuredLogger.getCorrelationId());
        
        StructuredLogger.clearCorrelationId();
        assertNull(StructuredLogger.getCorrelationId());
    }
    
    @Test
    void testMDCCleanup_AfterLogRequest() {
        structuredLogger.logRequest("req-1", "user-1", "session-1", "test");
        
        // Verify MDC is cleaned up after logging
        assertNull(MDC.get("eventType"));
    }
    
    @Test
    void testMDCCleanup_AfterLogResponse() {
        structuredLogger.logResponse("req-1", 200, "test", 100);
        
        // Verify MDC is cleaned up after logging
        assertNull(MDC.get("eventType"));
        assertNull(MDC.get("statusCode"));
        assertNull(MDC.get("durationMs"));
        assertNull(MDC.get("requestId"));
    }
    
    @Test
    void testMDCCleanup_AfterLogError() {
        structuredLogger.logError("req-1", "ERR001", "test", null);
        
        // Verify MDC is cleaned up after logging
        assertNull(MDC.get("eventType"));
        assertNull(MDC.get("errorCode"));
        assertNull(MDC.get("requestId"));
    }
}

package com.africanservices.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Session Manager Service for managing user conversation sessions.
 * Provides high-level session operations with unique ID generation and context window management.
 * 
 * Validates Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 3.5
 */
@Service
public class SessionManagerService {

    private static final Logger logger = LoggerFactory.getLogger(SessionManagerService.class);
    private static final int MAX_CONTEXT_TURNS = 5;

    private final DynamoDbService dynamoDbService;

    @Autowired
    public SessionManagerService(DynamoDbService dynamoDbService) {
        this.dynamoDbService = dynamoDbService;
    }

    /**
     * Create a new session with a unique session ID.
     * 
     * @param userId User identifier
     * @param language Detected or preferred language
     * @return Unique session ID
     */
    public String createSession(String userId, String language) {
        return createSession(userId, language, null);
    }

    /**
     * Create a new session with a unique session ID and optional context.
     * 
     * @param userId User identifier
     * @param language Detected or preferred language
     * @param context Optional session context map
     * @return Unique session ID
     */
    public String createSession(String userId, String language, Map<String, String> context) {
        String sessionId = generateUniqueSessionId();
        
        boolean created = dynamoDbService.createSession(sessionId, userId, language, context);
        
        if (created) {
            logger.info("Created new session: sessionId={}, userId={}, language={}", sessionId, userId, language);
            return sessionId;
        } else {
            logger.error("Failed to create session for userId={}", userId);
            throw new RuntimeException("Failed to create session");
        }
    }

    /**
     * Retrieve a session by ID.
     * 
     * @param sessionId Session identifier
     * @return Optional containing SessionData if found
     */
    public Optional<SessionData> getSession(String sessionId) {
        Optional<Map<String, AttributeValue>> sessionOpt = dynamoDbService.getSession(sessionId);
        
        if (sessionOpt.isEmpty()) {
            logger.debug("Session not found: sessionId={}", sessionId);
            return Optional.empty();
        }
        
        Map<String, AttributeValue> sessionMap = sessionOpt.get();
        SessionData sessionData = mapToSessionData(sessionMap);
        
        logger.debug("Retrieved session: sessionId={}, language={}", sessionId, sessionData.getLanguage());
        return Optional.of(sessionData);
    }

    /**
     * Update session with a new conversation turn.
     * Automatically manages context window (last 5 turns) and refreshes TTL.
     * 
     * @param sessionId Session identifier
     * @param userMessage User's message
     * @param assistantResponse Assistant's response
     * @return true if session was updated successfully
     */
    public boolean updateSession(String sessionId, String userMessage, String assistantResponse) {
        boolean updated = dynamoDbService.updateSession(sessionId, userMessage, assistantResponse);
        
        if (updated) {
            logger.debug("Updated session with new turn: sessionId={}", sessionId);
        } else {
            logger.error("Failed to update session: sessionId={}", sessionId);
        }
        
        return updated;
    }

    /**
     * Update session language preference.
     * 
     * @param sessionId Session identifier
     * @param language Language code
     * @return true if language was updated successfully
     */
    public boolean updateSessionLanguage(String sessionId, String language) {
        boolean updated = dynamoDbService.updateSessionLanguage(sessionId, language);
        
        if (updated) {
            logger.info("Updated session language: sessionId={}, language={}", sessionId, language);
        } else {
            logger.error("Failed to update session language: sessionId={}", sessionId);
        }
        
        return updated;
    }

    /**
     * Get conversation context for the session (last 5 turns only).
     * 
     * @param sessionId Session identifier
     * @return List of conversation turns, limited to last 5
     */
    public List<ConversationTurn> getConversationContext(String sessionId) {
        Optional<Map<String, AttributeValue>> sessionOpt = dynamoDbService.getSession(sessionId);
        
        if (sessionOpt.isEmpty()) {
            logger.debug("Session not found for context retrieval: sessionId={}", sessionId);
            return Collections.emptyList();
        }
        
        List<Map<String, AttributeValue>> history = dynamoDbService.getConversationHistory(
            sessionOpt.get(), 
            MAX_CONTEXT_TURNS
        );
        
        return history.stream()
            .map(this::mapToConversationTurn)
            .collect(Collectors.toList());
    }

    /**
     * Delete a session.
     * 
     * @param sessionId Session identifier
     * @return true if session was deleted successfully
     */
    public boolean deleteSession(String sessionId) {
        return dynamoDbService.deleteSession(sessionId);
    }

    // ==================== Helper Methods ====================

    /**
     * Generate a unique session ID using UUID.
     * 
     * @return Unique session identifier
     */
    private String generateUniqueSessionId() {
        return "session-" + UUID.randomUUID().toString();
    }

    /**
     * Map DynamoDB AttributeValue map to SessionData object.
     * 
     * @param sessionMap DynamoDB session data
     * @return SessionData object
     */
    private SessionData mapToSessionData(Map<String, AttributeValue> sessionMap) {
        String sessionId = sessionMap.get("sessionId").s();
        String userId = sessionMap.get("userId").s();
        String language = sessionMap.get("language").s();
        
        Long lastActivity = null;
        if (sessionMap.containsKey("lastActivity")) {
            lastActivity = Long.parseLong(sessionMap.get("lastActivity").n());
        }
        
        Map<String, String> context = new HashMap<>();
        if (sessionMap.containsKey("context") && sessionMap.get("context").hasM()) {
            Map<String, AttributeValue> contextMap = sessionMap.get("context").m();
            for (Map.Entry<String, AttributeValue> entry : contextMap.entrySet()) {
                context.put(entry.getKey(), entry.getValue().s());
            }
        }
        
        List<ConversationTurn> conversationHistory = dynamoDbService.getConversationHistory(
            sessionMap, 
            MAX_CONTEXT_TURNS
        ).stream()
            .map(this::mapToConversationTurn)
            .collect(Collectors.toList());
        
        return new SessionData(sessionId, userId, language, lastActivity, context, conversationHistory);
    }

    /**
     * Map DynamoDB AttributeValue map to ConversationTurn object.
     * 
     * @param turnMap DynamoDB conversation turn data
     * @return ConversationTurn object
     */
    private ConversationTurn mapToConversationTurn(Map<String, AttributeValue> turnMap) {
        long timestamp = Long.parseLong(turnMap.get("timestamp").n());
        String userMessage = turnMap.get("userMessage").s();
        String assistantResponse = turnMap.get("assistantResponse").s();
        
        return new ConversationTurn(timestamp, userMessage, assistantResponse);
    }

    // ==================== Data Classes ====================

    /**
     * Session data container.
     */
    public static class SessionData {
        private final String sessionId;
        private final String userId;
        private final String language;
        private final Long lastActivity;
        private final Map<String, String> context;
        private final List<ConversationTurn> conversationHistory;

        public SessionData(String sessionId, String userId, String language, Long lastActivity,
                          Map<String, String> context, List<ConversationTurn> conversationHistory) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.language = language;
            this.lastActivity = lastActivity;
            this.context = context != null ? context : new HashMap<>();
            this.conversationHistory = conversationHistory != null ? conversationHistory : new ArrayList<>();
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getUserId() {
            return userId;
        }

        public String getLanguage() {
            return language;
        }

        public Long getLastActivity() {
            return lastActivity;
        }

        public Map<String, String> getContext() {
            return context;
        }

        public List<ConversationTurn> getConversationHistory() {
            return conversationHistory;
        }
    }

    /**
     * Conversation turn container.
     */
    public static class ConversationTurn {
        private final long timestamp;
        private final String userMessage;
        private final String assistantResponse;

        public ConversationTurn(long timestamp, String userMessage, String assistantResponse) {
            this.timestamp = timestamp;
            this.userMessage = userMessage;
            this.assistantResponse = assistantResponse;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getUserMessage() {
            return userMessage;
        }

        public String getAssistantResponse() {
            return assistantResponse;
        }
    }
}

package com.africanservices.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionManagerService.
 * 
 * Tests Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 3.5
 */
@ExtendWith(MockitoExtension.class)
class SessionManagerServiceTest {

    @Mock
    private DynamoDbService dynamoDbService;

    private SessionManagerService sessionManagerService;

    @BeforeEach
    void setUp() {
        sessionManagerService = new SessionManagerService(dynamoDbService);
    }

    // ==================== Session Creation Tests ====================

    @Test
    void testCreateSession_WithoutContext_GeneratesUniqueId() {
        // Arrange
        String userId = "user123";
        String language = "ha";
        when(dynamoDbService.createSession(anyString(), eq(userId), eq(language), isNull()))
                .thenReturn(true);

        // Act
        String sessionId = sessionManagerService.createSession(userId, language);

        // Assert
        assertNotNull(sessionId);
        assertTrue(sessionId.startsWith("session-"));
        verify(dynamoDbService).createSession(eq(sessionId), eq(userId), eq(language), isNull());
    }

    @Test
    void testCreateSession_WithContext_StoresContext() {
        // Arrange
        String userId = "user123";
        String language = "yo";
        Map<String, String> context = Map.of("location", "Lagos", "serviceType", "health");
        when(dynamoDbService.createSession(anyString(), eq(userId), eq(language), eq(context)))
                .thenReturn(true);

        // Act
        String sessionId = sessionManagerService.createSession(userId, language, context);

        // Assert
        assertNotNull(sessionId);
        assertTrue(sessionId.startsWith("session-"));
        verify(dynamoDbService).createSession(eq(sessionId), eq(userId), eq(language), eq(context));
    }

    @Test
    void testCreateSession_GeneratesUniqueIds() {
        // Arrange
        String userId = "user123";
        String language = "en";
        when(dynamoDbService.createSession(anyString(), anyString(), anyString(), any()))
                .thenReturn(true);

        // Act
        String sessionId1 = sessionManagerService.createSession(userId, language);
        String sessionId2 = sessionManagerService.createSession(userId, language);

        // Assert
        assertNotEquals(sessionId1, sessionId2);
    }

    @Test
    void testCreateSession_FailsWhenDynamoDbFails() {
        // Arrange
        String userId = "user123";
        String language = "ig";
        when(dynamoDbService.createSession(anyString(), anyString(), anyString(), any()))
                .thenReturn(false);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> 
            sessionManagerService.createSession(userId, language)
        );
    }

    // ==================== Session Retrieval Tests ====================

    @Test
    void testGetSession_ReturnsSessionData_WhenExists() {
        // Arrange
        String sessionId = "session-123";
        Map<String, AttributeValue> sessionMap = createMockSessionMap(
            sessionId, "user123", "ha", 1234567890L
        );
        when(dynamoDbService.getSession(sessionId)).thenReturn(Optional.of(sessionMap));

        // Act
        Optional<SessionManagerService.SessionData> result = sessionManagerService.getSession(sessionId);

        // Assert
        assertTrue(result.isPresent());
        SessionManagerService.SessionData sessionData = result.get();
        assertEquals(sessionId, sessionData.getSessionId());
        assertEquals("user123", sessionData.getUserId());
        assertEquals("ha", sessionData.getLanguage());
        assertEquals(1234567890L, sessionData.getLastActivity());
    }

    @Test
    void testGetSession_ReturnsEmpty_WhenNotExists() {
        // Arrange
        String sessionId = "session-nonexistent";
        when(dynamoDbService.getSession(sessionId)).thenReturn(Optional.empty());

        // Act
        Optional<SessionManagerService.SessionData> result = sessionManagerService.getSession(sessionId);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetSession_ParsesContextCorrectly() {
        // Arrange
        String sessionId = "session-123";
        Map<String, AttributeValue> sessionMap = createMockSessionMapWithContext(
            sessionId, "user123", "yo", 
            Map.of("location", "Ibadan", "serviceType", "education")
        );
        when(dynamoDbService.getSession(sessionId)).thenReturn(Optional.of(sessionMap));

        // Act
        Optional<SessionManagerService.SessionData> result = sessionManagerService.getSession(sessionId);

        // Assert
        assertTrue(result.isPresent());
        Map<String, String> context = result.get().getContext();
        assertEquals("Ibadan", context.get("location"));
        assertEquals("education", context.get("serviceType"));
    }

    // ==================== Session Update Tests ====================

    @Test
    void testUpdateSession_CallsDynamoDbService() {
        // Arrange
        String sessionId = "session-123";
        String userMessage = "Where is the nearest hospital?";
        String assistantResponse = "The nearest hospital is General Hospital, 2km away.";
        when(dynamoDbService.updateSession(sessionId, userMessage, assistantResponse))
                .thenReturn(true);

        // Act
        boolean result = sessionManagerService.updateSession(sessionId, userMessage, assistantResponse);

        // Assert
        assertTrue(result);
        verify(dynamoDbService).updateSession(sessionId, userMessage, assistantResponse);
    }

    @Test
    void testUpdateSession_ReturnsFalse_WhenDynamoDbFails() {
        // Arrange
        String sessionId = "session-123";
        when(dynamoDbService.updateSession(anyString(), anyString(), anyString()))
                .thenReturn(false);

        // Act
        boolean result = sessionManagerService.updateSession(sessionId, "message", "response");

        // Assert
        assertFalse(result);
    }

    // ==================== Language Update Tests ====================

    @Test
    void testUpdateSessionLanguage_CallsDynamoDbService() {
        // Arrange
        String sessionId = "session-123";
        String language = "ff";
        when(dynamoDbService.updateSessionLanguage(sessionId, language))
                .thenReturn(true);

        // Act
        boolean result = sessionManagerService.updateSessionLanguage(sessionId, language);

        // Assert
        assertTrue(result);
        verify(dynamoDbService).updateSessionLanguage(sessionId, language);
    }

    @Test
    void testUpdateSessionLanguage_ReturnsFalse_WhenDynamoDbFails() {
        // Arrange
        String sessionId = "session-123";
        when(dynamoDbService.updateSessionLanguage(anyString(), anyString()))
                .thenReturn(false);

        // Act
        boolean result = sessionManagerService.updateSessionLanguage(sessionId, "en");

        // Assert
        assertFalse(result);
    }

    // ==================== Context Window Tests ====================

    @Test
    void testGetConversationContext_ReturnsLast5Turns() {
        // Arrange
        String sessionId = "session-123";
        Map<String, AttributeValue> sessionMap = createMockSessionMap(
            sessionId, "user123", "ha", 1234567890L
        );
        
        List<Map<String, AttributeValue>> history = createMockConversationHistory(7);
        
        when(dynamoDbService.getSession(sessionId)).thenReturn(Optional.of(sessionMap));
        when(dynamoDbService.getConversationHistory(sessionMap, 5))
                .thenReturn(history.subList(2, 7)); // Last 5 turns

        // Act
        List<SessionManagerService.ConversationTurn> context = 
            sessionManagerService.getConversationContext(sessionId);

        // Assert
        assertEquals(5, context.size());
        verify(dynamoDbService).getConversationHistory(sessionMap, 5);
    }

    @Test
    void testGetConversationContext_ReturnsEmpty_WhenSessionNotFound() {
        // Arrange
        String sessionId = "session-nonexistent";
        when(dynamoDbService.getSession(sessionId)).thenReturn(Optional.empty());

        // Act
        List<SessionManagerService.ConversationTurn> context = 
            sessionManagerService.getConversationContext(sessionId);

        // Assert
        assertTrue(context.isEmpty());
    }

    @Test
    void testGetConversationContext_ReturnsEmpty_WhenNoHistory() {
        // Arrange
        String sessionId = "session-123";
        Map<String, AttributeValue> sessionMap = createMockSessionMap(
            sessionId, "user123", "ha", 1234567890L
        );
        
        when(dynamoDbService.getSession(sessionId)).thenReturn(Optional.of(sessionMap));
        when(dynamoDbService.getConversationHistory(sessionMap, 5))
                .thenReturn(Collections.emptyList());

        // Act
        List<SessionManagerService.ConversationTurn> context = 
            sessionManagerService.getConversationContext(sessionId);

        // Assert
        assertTrue(context.isEmpty());
    }

    @Test
    void testGetConversationContext_ParsesTurnsCorrectly() {
        // Arrange
        String sessionId = "session-123";
        Map<String, AttributeValue> sessionMap = createMockSessionMap(
            sessionId, "user123", "yo", 1234567890L
        );
        
        List<Map<String, AttributeValue>> history = List.of(
            createMockTurn(1000L, "Hello", "Hi there!"),
            createMockTurn(2000L, "How are you?", "I'm doing well!")
        );
        
        when(dynamoDbService.getSession(sessionId)).thenReturn(Optional.of(sessionMap));
        when(dynamoDbService.getConversationHistory(sessionMap, 5))
                .thenReturn(history);

        // Act
        List<SessionManagerService.ConversationTurn> context = 
            sessionManagerService.getConversationContext(sessionId);

        // Assert
        assertEquals(2, context.size());
        assertEquals(1000L, context.get(0).getTimestamp());
        assertEquals("Hello", context.get(0).getUserMessage());
        assertEquals("Hi there!", context.get(0).getAssistantResponse());
        assertEquals(2000L, context.get(1).getTimestamp());
        assertEquals("How are you?", context.get(1).getUserMessage());
        assertEquals("I'm doing well!", context.get(1).getAssistantResponse());
    }

    // ==================== Session Deletion Tests ====================

    @Test
    void testDeleteSession_CallsDynamoDbService() {
        // Arrange
        String sessionId = "session-123";
        when(dynamoDbService.deleteSession(sessionId)).thenReturn(true);

        // Act
        boolean result = sessionManagerService.deleteSession(sessionId);

        // Assert
        assertTrue(result);
        verify(dynamoDbService).deleteSession(sessionId);
    }

    // ==================== Helper Methods ====================

    private Map<String, AttributeValue> createMockSessionMap(
            String sessionId, String userId, String language, Long lastActivity) {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("sessionId", AttributeValue.builder().s(sessionId).build());
        map.put("userId", AttributeValue.builder().s(userId).build());
        map.put("language", AttributeValue.builder().s(language).build());
        if (lastActivity != null) {
            map.put("lastActivity", AttributeValue.builder().n(String.valueOf(lastActivity)).build());
        }
        map.put("conversationHistory", AttributeValue.builder().l(Collections.emptyList()).build());
        return map;
    }

    private Map<String, AttributeValue> createMockSessionMapWithContext(
            String sessionId, String userId, String language, Map<String, String> context) {
        Map<String, AttributeValue> map = createMockSessionMap(sessionId, userId, language, null);
        
        Map<String, AttributeValue> contextMap = new HashMap<>();
        for (Map.Entry<String, String> entry : context.entrySet()) {
            contextMap.put(entry.getKey(), AttributeValue.builder().s(entry.getValue()).build());
        }
        map.put("context", AttributeValue.builder().m(contextMap).build());
        
        return map;
    }

    private List<Map<String, AttributeValue>> createMockConversationHistory(int count) {
        List<Map<String, AttributeValue>> history = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            history.add(createMockTurn(
                1000L * (i + 1),
                "User message " + i,
                "Assistant response " + i
            ));
        }
        return history;
    }

    private Map<String, AttributeValue> createMockTurn(
            long timestamp, String userMessage, String assistantResponse) {
        Map<String, AttributeValue> turn = new HashMap<>();
        turn.put("timestamp", AttributeValue.builder().n(String.valueOf(timestamp)).build());
        turn.put("userMessage", AttributeValue.builder().s(userMessage).build());
        turn.put("assistantResponse", AttributeValue.builder().s(assistantResponse).build());
        return turn;
    }
}

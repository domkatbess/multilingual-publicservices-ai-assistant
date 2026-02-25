package com.africanservices.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DynamoDbService.
 * Tests session CRUD operations and cache read/write operations.
 */
@ExtendWith(MockitoExtension.class)
class DynamoDbServiceTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private DynamoDbService dynamoDbService;

    @BeforeEach
    void setUp() {
        dynamoDbService = new DynamoDbService(dynamoDbClient);
    }

    // ==================== Session Creation Tests ====================

    @Test
    void testCreateSession_Success() {
        // Arrange
        String sessionId = "session-123";
        String userId = "user-456";
        String language = "en";
        Map<String, String> context = Map.of("intent", "health");

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        // Act
        boolean result = dynamoDbService.createSession(sessionId, userId, language, context);

        // Assert
        assertTrue(result);
        
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        
        PutItemRequest request = captor.getValue();
        Map<String, AttributeValue> item = request.item();
        
        assertEquals(sessionId, item.get("sessionId").s());
        assertEquals(userId, item.get("userId").s());
        assertEquals(language, item.get("language").s());
        assertNotNull(item.get("ttl"));
        assertNotNull(item.get("lastActivity"));
        assertTrue(item.containsKey("conversationHistory"));
    }

    @Test
    void testCreateSession_WithNullContext() {
        // Arrange
        String sessionId = "session-123";
        String userId = "user-456";
        String language = "ha";

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        // Act
        boolean result = dynamoDbService.createSession(sessionId, userId, language, null);

        // Assert
        assertTrue(result);
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void testCreateSession_DynamoDbException() {
        // Arrange
        String sessionId = "session-123";
        String userId = "user-456";
        String language = "yo";

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("Table not found").build());

        // Act
        boolean result = dynamoDbService.createSession(sessionId, userId, language, null);

        // Assert
        assertFalse(result);
    }

    // ==================== Session Retrieval Tests ====================

    @Test
    void testGetSession_Success() {
        // Arrange
        String sessionId = "session-123";
        Map<String, AttributeValue> sessionData = new HashMap<>();
        sessionData.put("sessionId", AttributeValue.builder().s(sessionId).build());
        sessionData.put("userId", AttributeValue.builder().s("user-456").build());
        sessionData.put("language", AttributeValue.builder().s("en").build());

        GetItemResponse response = GetItemResponse.builder()
                .item(sessionData)
                .build();

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(response);

        // Act
        Optional<Map<String, AttributeValue>> result = dynamoDbService.getSession(sessionId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(sessionId, result.get().get("sessionId").s());
        assertEquals("user-456", result.get().get("userId").s());
        assertEquals("en", result.get().get("language").s());
    }

    @Test
    void testGetSession_NotFound() {
        // Arrange
        String sessionId = "session-nonexistent";
        GetItemResponse response = GetItemResponse.builder()
                .item(Collections.emptyMap())
                .build();

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(response);

        // Act
        Optional<Map<String, AttributeValue>> result = dynamoDbService.getSession(sessionId);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetSession_DynamoDbException() {
        // Arrange
        String sessionId = "session-123";
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("Service unavailable").build());

        // Act
        Optional<Map<String, AttributeValue>> result = dynamoDbService.getSession(sessionId);

        // Assert
        assertFalse(result.isPresent());
    }

    // ==================== Session Update Tests ====================

    @Test
    void testUpdateSession_Success() {
        // Arrange
        String sessionId = "session-123";
        String userMessage = "What are the health services?";
        String assistantResponse = "Here are the available health services...";

        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        // Act
        boolean result = dynamoDbService.updateSession(sessionId, userMessage, assistantResponse);

        // Assert
        assertTrue(result);
        
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        
        UpdateItemRequest request = captor.getValue();
        assertTrue(request.updateExpression().contains("conversationHistory"));
        assertTrue(request.updateExpression().contains("lastActivity"));
        assertTrue(request.updateExpression().contains("ttl"));
    }

    @Test
    void testUpdateSession_DynamoDbException() {
        // Arrange
        String sessionId = "session-123";
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("Update failed").build());

        // Act
        boolean result = dynamoDbService.updateSession(sessionId, "message", "response");

        // Assert
        assertFalse(result);
    }

    @Test
    void testUpdateSessionLanguage_Success() {
        // Arrange
        String sessionId = "session-123";
        String language = "ha";

        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        // Act
        boolean result = dynamoDbService.updateSessionLanguage(sessionId, language);

        // Assert
        assertTrue(result);
        
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        
        UpdateItemRequest request = captor.getValue();
        assertTrue(request.updateExpression().contains("language"));
    }

    // ==================== Session Deletion Tests ====================

    @Test
    void testDeleteSession_Success() {
        // Arrange
        String sessionId = "session-123";
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(DeleteItemResponse.builder().build());

        // Act
        boolean result = dynamoDbService.deleteSession(sessionId);

        // Assert
        assertTrue(result);
        verify(dynamoDbClient).deleteItem(any(DeleteItemRequest.class));
    }

    @Test
    void testDeleteSession_DynamoDbException() {
        // Arrange
        String sessionId = "session-123";
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("Delete failed").build());

        // Act
        boolean result = dynamoDbService.deleteSession(sessionId);

        // Assert
        assertFalse(result);
    }

    // ==================== Cache Operations Tests ====================

    @Test
    void testGetCachedResponse_CacheHit() {
        // Arrange
        String queryHash = "hash-123";
        String language = "en";
        String cachedResponse = "This is a cached response";
        long futureTtl = Instant.now().plusSeconds(3600).getEpochSecond();

        Map<String, AttributeValue> cacheData = new HashMap<>();
        cacheData.put("queryHash", AttributeValue.builder().s(queryHash).build());
        cacheData.put("language", AttributeValue.builder().s(language).build());
        cacheData.put("response", AttributeValue.builder().s(cachedResponse).build());
        cacheData.put("ttl", AttributeValue.builder().n(String.valueOf(futureTtl)).build());
        cacheData.put("hitCount", AttributeValue.builder().n("5").build());

        GetItemResponse getResponse = GetItemResponse.builder()
                .item(cacheData)
                .build();

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(getResponse);
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        // Act
        Optional<String> result = dynamoDbService.getCachedResponse(queryHash, language);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(cachedResponse, result.get());
        
        // Verify hit count was incremented
        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void testGetCachedResponse_CacheMiss() {
        // Arrange
        String queryHash = "hash-nonexistent";
        String language = "en";

        GetItemResponse response = GetItemResponse.builder()
                .item(Collections.emptyMap())
                .build();

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(response);

        // Act
        Optional<String> result = dynamoDbService.getCachedResponse(queryHash, language);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetCachedResponse_Expired() {
        // Arrange
        String queryHash = "hash-123";
        String language = "en";
        long pastTtl = Instant.now().minusSeconds(3600).getEpochSecond();

        Map<String, AttributeValue> cacheData = new HashMap<>();
        cacheData.put("queryHash", AttributeValue.builder().s(queryHash).build());
        cacheData.put("language", AttributeValue.builder().s(language).build());
        cacheData.put("response", AttributeValue.builder().s("Expired response").build());
        cacheData.put("ttl", AttributeValue.builder().n(String.valueOf(pastTtl)).build());

        GetItemResponse response = GetItemResponse.builder()
                .item(cacheData)
                .build();

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(response);

        // Act
        Optional<String> result = dynamoDbService.getCachedResponse(queryHash, language);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testCacheResponse_Success() {
        // Arrange
        String queryHash = "hash-123";
        String language = "en";
        String response = "This is a response to cache";

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        // Act
        boolean result = dynamoDbService.cacheResponse(queryHash, language, response);

        // Assert
        assertTrue(result);
        
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        
        PutItemRequest request = captor.getValue();
        Map<String, AttributeValue> item = request.item();
        
        assertEquals(queryHash, item.get("queryHash").s());
        assertEquals(language, item.get("language").s());
        assertEquals(response, item.get("response").s());
        assertNotNull(item.get("ttl"));
        assertEquals("0", item.get("hitCount").n());
    }

    @Test
    void testCacheResponse_DynamoDbException() {
        // Arrange
        String queryHash = "hash-123";
        String language = "en";
        String response = "Response";

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("Cache write failed").build());

        // Act
        boolean result = dynamoDbService.cacheResponse(queryHash, language, response);

        // Assert
        assertFalse(result);
    }

    // ==================== Conversation History Tests ====================

    @Test
    void testGetConversationHistory_WithMultipleTurns() {
        // Arrange
        List<AttributeValue> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, AttributeValue> turn = new HashMap<>();
            turn.put("timestamp", AttributeValue.builder().n(String.valueOf(Instant.now().getEpochSecond())).build());
            turn.put("userMessage", AttributeValue.builder().s("Message " + i).build());
            turn.put("assistantResponse", AttributeValue.builder().s("Response " + i).build());
            history.add(AttributeValue.builder().m(turn).build());
        }

        Map<String, AttributeValue> sessionData = new HashMap<>();
        sessionData.put("conversationHistory", AttributeValue.builder().l(history).build());

        // Act - Get last 5 turns
        List<Map<String, AttributeValue>> result = dynamoDbService.getConversationHistory(sessionData, 5);

        // Assert
        assertEquals(5, result.size());
        assertEquals("Message 5", result.get(0).get("userMessage").s());
        assertEquals("Message 9", result.get(4).get("userMessage").s());
    }

    @Test
    void testGetConversationHistory_LessThanMaxTurns() {
        // Arrange
        List<AttributeValue> history = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, AttributeValue> turn = new HashMap<>();
            turn.put("userMessage", AttributeValue.builder().s("Message " + i).build());
            history.add(AttributeValue.builder().m(turn).build());
        }

        Map<String, AttributeValue> sessionData = new HashMap<>();
        sessionData.put("conversationHistory", AttributeValue.builder().l(history).build());

        // Act - Request 5 turns but only 3 exist
        List<Map<String, AttributeValue>> result = dynamoDbService.getConversationHistory(sessionData, 5);

        // Assert
        assertEquals(3, result.size());
    }

    @Test
    void testGetConversationHistory_EmptyHistory() {
        // Arrange
        Map<String, AttributeValue> sessionData = new HashMap<>();
        sessionData.put("conversationHistory", AttributeValue.builder().l(Collections.emptyList()).build());

        // Act
        List<Map<String, AttributeValue>> result = dynamoDbService.getConversationHistory(sessionData, 5);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetConversationHistory_NoHistoryField() {
        // Arrange
        Map<String, AttributeValue> sessionData = new HashMap<>();

        // Act
        List<Map<String, AttributeValue>> result = dynamoDbService.getConversationHistory(sessionData, 5);

        // Assert
        assertTrue(result.isEmpty());
    }
}

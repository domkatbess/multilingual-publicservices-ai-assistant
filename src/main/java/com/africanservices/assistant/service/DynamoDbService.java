package com.africanservices.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

/**
 * DynamoDB client wrapper service for session and cache operations.
 * Provides CRUD operations for Sessions table and read/write operations for ResponseCache table.
 * 
 * Validates Requirements: 6.1, 6.2, 6.4, 6.5, 7.2, 7.5, 10.5
 */
@Service
public class DynamoDbService {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbService.class);
    private static final int SESSION_TTL_MINUTES = 30;
    private static final int CACHE_TTL_HOURS = 24;

    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.dynamodb.sessions-table:language-assistant-sessions-dev}")
    private String sessionsTableName;

    @Value("${aws.dynamodb.cache-table:language-assistant-cache-dev}")
    private String cacheTableName;

    @Autowired
    public DynamoDbService(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.objectMapper = new ObjectMapper();
    }

    // ==================== Session Operations ====================

    /**
     * Create a new session in DynamoDB.
     * 
     * @param sessionId Unique session identifier
     * @param userId User identifier
     * @param language Detected or preferred language
     * @param context Session context map
     * @return true if session was created successfully
     */
    public boolean createSession(String sessionId, String userId, String language, Map<String, String> context) {
        try {
            long ttl = calculateSessionTtl();
            
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("sessionId", AttributeValue.builder().s(sessionId).build());
            item.put("userId", AttributeValue.builder().s(userId).build());
            item.put("language", AttributeValue.builder().s(language).build());
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
            item.put("lastActivity", AttributeValue.builder().n(String.valueOf(Instant.now().getEpochSecond())).build());
            
            if (context != null && !context.isEmpty()) {
                item.put("context", convertMapToAttributeValue(context));
            }
            
            // Initialize empty conversation history
            item.put("conversationHistory", AttributeValue.builder().l(Collections.emptyList()).build());

            PutItemRequest request = PutItemRequest.builder()
                    .tableName(sessionsTableName)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(request);
            logger.info("Created session: sessionId={}, userId={}, language={}", sessionId, userId, language);
            return true;
        } catch (DynamoDbException e) {
            logger.error("Failed to create session: sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * Retrieve a session from DynamoDB.
     * 
     * @param sessionId Session identifier
     * @return Optional containing session data if found
     */
    public Optional<Map<String, AttributeValue>> getSession(String sessionId) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("sessionId", AttributeValue.builder().s(sessionId).build());

            GetItemRequest request = GetItemRequest.builder()
                    .tableName(sessionsTableName)
                    .key(key)
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);
            
            if (response.hasItem() && !response.item().isEmpty()) {
                logger.debug("Retrieved session: sessionId={}", sessionId);
                return Optional.of(response.item());
            } else {
                logger.debug("Session not found: sessionId={}", sessionId);
                return Optional.empty();
            }
        } catch (DynamoDbException e) {
            logger.error("Failed to retrieve session: sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }

    /**
     * Update session with new conversation turn and refresh TTL.
     * 
     * @param sessionId Session identifier
     * @param userMessage User's message
     * @param assistantResponse Assistant's response
     * @return true if session was updated successfully
     */
    public boolean updateSession(String sessionId, String userMessage, String assistantResponse) {
        try {
            long ttl = calculateSessionTtl();
            long timestamp = Instant.now().getEpochSecond();

            // Create conversation turn
            Map<String, AttributeValue> turn = new HashMap<>();
            turn.put("timestamp", AttributeValue.builder().n(String.valueOf(timestamp)).build());
            turn.put("userMessage", AttributeValue.builder().s(userMessage).build());
            turn.put("assistantResponse", AttributeValue.builder().s(assistantResponse).build());

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("sessionId", AttributeValue.builder().s(sessionId).build());

            // Update expression to append conversation turn and update TTL
            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(sessionsTableName)
                    .key(key)
                    .updateExpression("SET conversationHistory = list_append(if_not_exists(conversationHistory, :empty_list), :turn), " +
                                    "lastActivity = :timestamp, " +
                                    "ttl = :ttl")
                    .expressionAttributeValues(Map.of(
                            ":turn", AttributeValue.builder().l(AttributeValue.builder().m(turn).build()).build(),
                            ":timestamp", AttributeValue.builder().n(String.valueOf(timestamp)).build(),
                            ":ttl", AttributeValue.builder().n(String.valueOf(ttl)).build(),
                            ":empty_list", AttributeValue.builder().l(Collections.emptyList()).build()
                    ))
                    .build();

            dynamoDbClient.updateItem(request);
            logger.debug("Updated session: sessionId={}", sessionId);
            return true;
        } catch (DynamoDbException e) {
            logger.error("Failed to update session: sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * Update session language preference.
     * 
     * @param sessionId Session identifier
     * @param language Language code
     * @return true if language was updated successfully
     */
    public boolean updateSessionLanguage(String sessionId, String language) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("sessionId", AttributeValue.builder().s(sessionId).build());

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(sessionsTableName)
                    .key(key)
                    .updateExpression("SET language = :language")
                    .expressionAttributeValues(Map.of(
                            ":language", AttributeValue.builder().s(language).build()
                    ))
                    .build();

            dynamoDbClient.updateItem(request);
            logger.debug("Updated session language: sessionId={}, language={}", sessionId, language);
            return true;
        } catch (DynamoDbException e) {
            logger.error("Failed to update session language: sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * Delete a session from DynamoDB.
     * 
     * @param sessionId Session identifier
     * @return true if session was deleted successfully
     */
    public boolean deleteSession(String sessionId) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("sessionId", AttributeValue.builder().s(sessionId).build());

            DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(sessionsTableName)
                    .key(key)
                    .build();

            dynamoDbClient.deleteItem(request);
            logger.info("Deleted session: sessionId={}", sessionId);
            return true;
        } catch (DynamoDbException e) {
            logger.error("Failed to delete session: sessionId={}", sessionId, e);
            return false;
        }
    }

    // ==================== Cache Operations ====================

    /**
     * Retrieve cached response from DynamoDB.
     * 
     * @param queryHash Hash of the query
     * @param language Language code
     * @return Optional containing cached response if found and not expired
     */
    public Optional<String> getCachedResponse(String queryHash, String language) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("queryHash", AttributeValue.builder().s(queryHash).build());
            key.put("language", AttributeValue.builder().s(language).build());

            GetItemRequest request = GetItemRequest.builder()
                    .tableName(cacheTableName)
                    .key(key)
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);
            
            if (response.hasItem() && !response.item().isEmpty()) {
                Map<String, AttributeValue> item = response.item();
                
                // Check if cache entry is expired
                if (item.containsKey("ttl")) {
                    long ttl = Long.parseLong(item.get("ttl").n());
                    if (Instant.now().getEpochSecond() > ttl) {
                        logger.debug("Cache entry expired: queryHash={}, language={}", queryHash, language);
                        return Optional.empty();
                    }
                }
                
                String cachedResponse = item.get("response").s();
                logger.debug("Cache hit: queryHash={}, language={}", queryHash, language);
                
                // Increment hit count asynchronously
                incrementCacheHitCount(queryHash, language);
                
                return Optional.of(cachedResponse);
            } else {
                logger.debug("Cache miss: queryHash={}, language={}", queryHash, language);
                return Optional.empty();
            }
        } catch (DynamoDbException e) {
            logger.error("Failed to retrieve cached response: queryHash={}, language={}", queryHash, language, e);
            return Optional.empty();
        }
    }

    /**
     * Store response in cache with TTL.
     * 
     * @param queryHash Hash of the query
     * @param language Language code
     * @param response Response text to cache
     * @return true if response was cached successfully
     */
    public boolean cacheResponse(String queryHash, String language, String response) {
        try {
            long ttl = calculateCacheTtl();
            long timestamp = Instant.now().getEpochSecond();

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("queryHash", AttributeValue.builder().s(queryHash).build());
            item.put("language", AttributeValue.builder().s(language).build());
            item.put("response", AttributeValue.builder().s(response).build());
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
            item.put("createdAt", AttributeValue.builder().n(String.valueOf(timestamp)).build());
            item.put("hitCount", AttributeValue.builder().n("0").build());

            PutItemRequest request = PutItemRequest.builder()
                    .tableName(cacheTableName)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(request);
            logger.debug("Cached response: queryHash={}, language={}", queryHash, language);
            return true;
        } catch (DynamoDbException e) {
            logger.error("Failed to cache response: queryHash={}, language={}", queryHash, language, e);
            return false;
        }
    }

    /**
     * Increment cache hit count for analytics.
     * 
     * @param queryHash Hash of the query
     * @param language Language code
     */
    private void incrementCacheHitCount(String queryHash, String language) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("queryHash", AttributeValue.builder().s(queryHash).build());
            key.put("language", AttributeValue.builder().s(language).build());

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(cacheTableName)
                    .key(key)
                    .updateExpression("SET hitCount = if_not_exists(hitCount, :zero) + :inc")
                    .expressionAttributeValues(Map.of(
                            ":inc", AttributeValue.builder().n("1").build(),
                            ":zero", AttributeValue.builder().n("0").build()
                    ))
                    .build();

            dynamoDbClient.updateItem(request);
            logger.trace("Incremented cache hit count: queryHash={}, language={}", queryHash, language);
        } catch (DynamoDbException e) {
            // Log but don't fail - hit count is for analytics only
            logger.warn("Failed to increment cache hit count: queryHash={}, language={}", queryHash, language, e);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Calculate TTL for session (30 minutes from now).
     * 
     * @return TTL timestamp in seconds
     */
    private long calculateSessionTtl() {
        return Instant.now().plusSeconds(SESSION_TTL_MINUTES * 60L).getEpochSecond();
    }

    /**
     * Calculate TTL for cache (24 hours from now).
     * 
     * @return TTL timestamp in seconds
     */
    private long calculateCacheTtl() {
        return Instant.now().plusSeconds(CACHE_TTL_HOURS * 3600L).getEpochSecond();
    }

    /**
     * Convert a Map to DynamoDB AttributeValue.
     * 
     * @param map Map to convert
     * @return AttributeValue containing the map
     */
    private AttributeValue convertMapToAttributeValue(Map<String, String> map) {
        Map<String, AttributeValue> attributeMap = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            attributeMap.put(entry.getKey(), AttributeValue.builder().s(entry.getValue()).build());
        }
        return AttributeValue.builder().m(attributeMap).build();
    }

    /**
     * Get conversation history from session, limited to last N turns.
     * 
     * @param sessionData Session data from DynamoDB
     * @param maxTurns Maximum number of turns to retrieve
     * @return List of conversation turns
     */
    public List<Map<String, AttributeValue>> getConversationHistory(Map<String, AttributeValue> sessionData, int maxTurns) {
        if (!sessionData.containsKey("conversationHistory")) {
            return Collections.emptyList();
        }

        List<AttributeValue> history = sessionData.get("conversationHistory").l();
        
        // Return last N turns
        int startIndex = Math.max(0, history.size() - maxTurns);
        List<Map<String, AttributeValue>> result = new ArrayList<>();
        
        for (int i = startIndex; i < history.size(); i++) {
            result.add(history.get(i).m());
        }
        
        return result;
    }
}

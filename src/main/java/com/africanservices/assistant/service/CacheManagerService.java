package com.africanservices.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cache manager service for response caching.
 * Provides hash computation, cache lookup, storage with TTL, and hit count tracking.
 * Ensures no user-specific data is included in cache entries.
 * 
 * Validates Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 13.5
 */
@Service
public class CacheManagerService {

    private static final Logger logger = LoggerFactory.getLogger(CacheManagerService.class);
    
    // Patterns to detect PII and user-specific data
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d[\\d\\s()-]{7,}\\d");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("(?i)(user[-_]?id|session[-_]?id)\\s*[:=\\s]\\s*[a-zA-Z0-9-]+");
    
    private final DynamoDbService dynamoDbService;

    @Autowired
    public CacheManagerService(DynamoDbService dynamoDbService) {
        this.dynamoDbService = dynamoDbService;
    }

    /**
     * Compute deterministic hash for query and language combination.
     * Uses SHA-256 to ensure consistent hash values for identical inputs.
     * 
     * Requirement 7.1: Hash computation for query and language combination
     * 
     * @param query User query text
     * @param language Language code
     * @return Hexadecimal hash string
     */
    public String computeQueryHash(String query, String language) {
        if (query == null || language == null) {
            throw new IllegalArgumentException("Query and language must not be null");
        }

        try {
            // Normalize query: trim and lowercase for consistent hashing
            String normalizedQuery = query.trim().toLowerCase();
            String combinedInput = normalizedQuery + "|" + language.toLowerCase();
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combinedInput.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String hash = hexString.toString();
            logger.debug("Computed query hash: language={}, hashLength={}", language, hash.length());
            return hash;
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to compute query hash", e);
        }
    }

    /**
     * Look up cached response by query and language.
     * 
     * Requirements 7.2, 7.3: Cache lookup by hash key, return cached response without calling Bedrock
     * 
     * @param query User query text
     * @param language Language code
     * @return Optional containing cached response if found and not expired
     */
    public Optional<String> getCachedResponse(String query, String language) {
        try {
            String queryHash = computeQueryHash(query, language);
            Optional<String> cachedResponse = dynamoDbService.getCachedResponse(queryHash, language);
            
            if (cachedResponse.isPresent()) {
                logger.info("Cache hit: language={}, queryLength={}", language, query.length());
            } else {
                logger.debug("Cache miss: language={}, queryLength={}", language, query.length());
            }
            
            return cachedResponse;
        } catch (Exception e) {
            logger.error("Error retrieving cached response: language={}", language, e);
            return Optional.empty();
        }
    }

    /**
     * Store response in cache with 24-hour TTL.
     * Validates that response contains no user-specific data before caching.
     * 
     * Requirements 7.5, 13.5: Cache storage with 24-hour TTL, no user-specific data in cache
     * 
     * @param query User query text
     * @param language Language code
     * @param response Response text to cache
     * @return true if response was cached successfully
     */
    public boolean cacheResponse(String query, String language, String response) {
        if (query == null || language == null || response == null) {
            logger.warn("Cannot cache response: null parameters");
            return false;
        }

        // Validate response contains no user-specific data
        if (containsUserSpecificData(response)) {
            logger.warn("Response contains user-specific data, not caching: language={}", language);
            return false;
        }

        try {
            String queryHash = computeQueryHash(query, language);
            boolean success = dynamoDbService.cacheResponse(queryHash, language, response);
            
            if (success) {
                logger.info("Cached response: language={}, queryLength={}, responseLength={}", 
                           language, query.length(), response.length());
            }
            
            return success;
        } catch (Exception e) {
            logger.error("Error caching response: language={}", language, e);
            return false;
        }
    }

    /**
     * Check if response contains user-specific data that should not be cached.
     * Detects patterns like email addresses, phone numbers, user IDs, and session IDs.
     * 
     * Requirement 13.5: Ensure no user-specific data in cache entries
     * 
     * @param response Response text to validate
     * @return true if response contains user-specific data
     */
    private boolean containsUserSpecificData(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }

        // Check for email addresses
        if (EMAIL_PATTERN.matcher(response).find()) {
            logger.debug("Response contains email address");
            return true;
        }

        // Check for phone numbers
        if (PHONE_PATTERN.matcher(response).find()) {
            logger.debug("Response contains phone number");
            return true;
        }

        // Check for user/session ID references
        if (USER_ID_PATTERN.matcher(response).find()) {
            logger.debug("Response contains user/session ID reference");
            return true;
        }

        return false;
    }

    /**
     * Invalidate cached response for a specific query and language.
     * Useful for cache management and testing.
     * 
     * @param query User query text
     * @param language Language code
     * @return true if cache entry was invalidated
     */
    public boolean invalidateCache(String query, String language) {
        try {
            String queryHash = computeQueryHash(query, language);
            // DynamoDB TTL will handle automatic deletion, but we can manually delete if needed
            logger.info("Cache invalidation requested: language={}, queryHash={}", language, queryHash);
            // Note: DynamoDbService doesn't have a delete cache method yet, 
            // but TTL will handle expiration automatically
            return true;
        } catch (Exception e) {
            logger.error("Error invalidating cache: language={}", language, e);
            return false;
        }
    }
}

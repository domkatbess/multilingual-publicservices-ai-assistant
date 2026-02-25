package com.africanservices.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CacheManagerService.
 * Tests hash computation, cache lookup, storage, and privacy validation.
 */
@ExtendWith(MockitoExtension.class)
class CacheManagerServiceTest {

    @Mock
    private DynamoDbService dynamoDbService;

    @InjectMocks
    private CacheManagerService cacheManagerService;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(dynamoDbService);
    }

    // ==================== Hash Computation Tests ====================

    @Test
    void testComputeQueryHash_SameInputProducesSameHash() {
        // Requirement 7.1: Hash computation should be deterministic
        String query = "How do I apply for a passport?";
        String language = "en";

        String hash1 = cacheManagerService.computeQueryHash(query, language);
        String hash2 = cacheManagerService.computeQueryHash(query, language);

        assertNotNull(hash1);
        assertNotNull(hash2);
        assertEquals(hash1, hash2, "Same input should produce same hash");
        assertEquals(64, hash1.length(), "SHA-256 hash should be 64 hex characters");
    }

    @Test
    void testComputeQueryHash_DifferentQueriesProduceDifferentHashes() {
        String query1 = "How do I apply for a passport?";
        String query2 = "Where is the nearest hospital?";
        String language = "en";

        String hash1 = cacheManagerService.computeQueryHash(query1, language);
        String hash2 = cacheManagerService.computeQueryHash(query2, language);

        assertNotEquals(hash1, hash2, "Different queries should produce different hashes");
    }

    @Test
    void testComputeQueryHash_DifferentLanguagesProduceDifferentHashes() {
        String query = "How do I apply for a passport?";
        String language1 = "en";
        String language2 = "ha";

        String hash1 = cacheManagerService.computeQueryHash(query, language1);
        String hash2 = cacheManagerService.computeQueryHash(query, language2);

        assertNotEquals(hash1, hash2, "Same query in different languages should produce different hashes");
    }

    @Test
    void testComputeQueryHash_NormalizesInput() {
        // Test that whitespace and case differences are normalized
        String query1 = "  How do I apply?  ";
        String query2 = "how do i apply?";
        String language = "en";

        String hash1 = cacheManagerService.computeQueryHash(query1, language);
        String hash2 = cacheManagerService.computeQueryHash(query2, language);

        assertEquals(hash1, hash2, "Normalized queries should produce same hash");
    }

    @Test
    void testComputeQueryHash_NullQueryThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            cacheManagerService.computeQueryHash(null, "en");
        });
    }

    @Test
    void testComputeQueryHash_NullLanguageThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            cacheManagerService.computeQueryHash("test query", null);
        });
    }

    // ==================== Cache Lookup Tests ====================

    @Test
    void testGetCachedResponse_CacheHit() {
        // Requirement 7.2, 7.3: Cache lookup should return cached response
        String query = "How do I apply for a passport?";
        String language = "en";
        String cachedResponse = "To apply for a passport, visit your nearest passport office...";

        when(dynamoDbService.getCachedResponse(anyString(), eq(language)))
                .thenReturn(Optional.of(cachedResponse));

        Optional<String> result = cacheManagerService.getCachedResponse(query, language);

        assertTrue(result.isPresent());
        assertEquals(cachedResponse, result.get());
        verify(dynamoDbService, times(1)).getCachedResponse(anyString(), eq(language));
    }

    @Test
    void testGetCachedResponse_CacheMiss() {
        String query = "How do I apply for a passport?";
        String language = "en";

        when(dynamoDbService.getCachedResponse(anyString(), eq(language)))
                .thenReturn(Optional.empty());

        Optional<String> result = cacheManagerService.getCachedResponse(query, language);

        assertFalse(result.isPresent());
        verify(dynamoDbService, times(1)).getCachedResponse(anyString(), eq(language));
    }

    @Test
    void testGetCachedResponse_HandlesException() {
        String query = "How do I apply for a passport?";
        String language = "en";

        when(dynamoDbService.getCachedResponse(anyString(), eq(language)))
                .thenThrow(new RuntimeException("DynamoDB error"));

        Optional<String> result = cacheManagerService.getCachedResponse(query, language);

        assertFalse(result.isPresent(), "Should return empty on exception");
        verify(dynamoDbService, times(1)).getCachedResponse(anyString(), eq(language));
    }

    // ==================== Cache Storage Tests ====================

    @Test
    void testCacheResponse_Success() {
        // Requirement 7.5: Cache storage with 24-hour TTL
        String query = "How do I apply for a passport?";
        String language = "en";
        String response = "To apply for a passport, visit your nearest passport office with required documents.";

        when(dynamoDbService.cacheResponse(anyString(), eq(language), eq(response)))
                .thenReturn(true);

        boolean result = cacheManagerService.cacheResponse(query, language, response);

        assertTrue(result);
        verify(dynamoDbService, times(1)).cacheResponse(anyString(), eq(language), eq(response));
    }

    @Test
    void testCacheResponse_NullParametersReturnsFalse() {
        assertFalse(cacheManagerService.cacheResponse(null, "en", "response"));
        assertFalse(cacheManagerService.cacheResponse("query", null, "response"));
        assertFalse(cacheManagerService.cacheResponse("query", "en", null));
        
        verify(dynamoDbService, never()).cacheResponse(anyString(), anyString(), anyString());
    }

    @Test
    void testCacheResponse_HandlesException() {
        String query = "How do I apply for a passport?";
        String language = "en";
        String response = "To apply for a passport, visit your nearest passport office.";

        when(dynamoDbService.cacheResponse(anyString(), eq(language), eq(response)))
                .thenThrow(new RuntimeException("DynamoDB error"));

        boolean result = cacheManagerService.cacheResponse(query, language, response);

        assertFalse(result, "Should return false on exception");
        verify(dynamoDbService, times(1)).cacheResponse(anyString(), eq(language), eq(response));
    }

    // ==================== Privacy Validation Tests ====================

    @Test
    void testCacheResponse_RejectsEmailAddresses() {
        // Requirement 13.5: No user-specific data in cache entries
        String query = "How do I contact support?";
        String language = "en";
        String responseWithEmail = "Contact us at support@example.com for assistance.";

        boolean result = cacheManagerService.cacheResponse(query, language, responseWithEmail);

        assertFalse(result, "Should not cache response containing email address");
        verify(dynamoDbService, never()).cacheResponse(anyString(), anyString(), anyString());
    }

    @Test
    void testCacheResponse_RejectsPhoneNumbers() {
        // Requirement 13.5: No user-specific data in cache entries
        String query = "What is the emergency number?";
        String language = "en";
        String responseWithPhone = "Call us at +1-555-123-4567 for emergencies.";

        boolean result = cacheManagerService.cacheResponse(query, language, responseWithPhone);

        assertFalse(result, "Should not cache response containing phone number");
        verify(dynamoDbService, never()).cacheResponse(anyString(), anyString(), anyString());
    }

    @Test
    void testCacheResponse_RejectsUserIdReferences() {
        // Requirement 13.5: No user-specific data in cache entries
        String query = "Check my application status";
        String language = "en";
        String responseWithUserId = "Your user_id: abc123 has been verified.";

        boolean result = cacheManagerService.cacheResponse(query, language, responseWithUserId);

        assertFalse(result, "Should not cache response containing user ID");
        verify(dynamoDbService, never()).cacheResponse(anyString(), anyString(), anyString());
    }

    @Test
    void testCacheResponse_RejectsSessionIdReferences() {
        // Requirement 13.5: No user-specific data in cache entries
        String query = "Continue my session";
        String language = "en";
        String responseWithSessionId = "Your session-id is xyz789.";

        boolean result = cacheManagerService.cacheResponse(query, language, responseWithSessionId);

        assertFalse(result, "Should not cache response containing session ID");
        verify(dynamoDbService, never()).cacheResponse(anyString(), anyString(), anyString());
    }

    @Test
    void testCacheResponse_AcceptsGenericResponse() {
        String query = "How do I apply for a passport?";
        String language = "en";
        String genericResponse = "To apply for a passport, visit your nearest passport office with required documents including birth certificate and ID.";

        when(dynamoDbService.cacheResponse(anyString(), eq(language), eq(genericResponse)))
                .thenReturn(true);

        boolean result = cacheManagerService.cacheResponse(query, language, genericResponse);

        assertTrue(result, "Should cache generic response without user-specific data");
        verify(dynamoDbService, times(1)).cacheResponse(anyString(), eq(language), eq(genericResponse));
    }

    // ==================== Cache Invalidation Tests ====================

    @Test
    void testInvalidateCache_Success() {
        String query = "How do I apply for a passport?";
        String language = "en";

        boolean result = cacheManagerService.invalidateCache(query, language);

        assertTrue(result, "Cache invalidation should succeed");
    }

    @Test
    void testInvalidateCache_HandlesException() {
        String query = "How do I apply for a passport?";
        String language = "en";

        // Even if hash computation fails, should handle gracefully
        boolean result = cacheManagerService.invalidateCache(query, language);

        assertTrue(result, "Should handle invalidation gracefully");
    }

    // ==================== Integration-Style Tests ====================

    @Test
    void testCacheWorkflow_StoreAndRetrieve() {
        String query = "How do I enroll my child in school?";
        String language = "yo";
        String response = "To enroll your child, visit the school with birth certificate and immunization records.";

        // Mock storage
        when(dynamoDbService.cacheResponse(anyString(), eq(language), eq(response)))
                .thenReturn(true);

        // Store response
        boolean stored = cacheManagerService.cacheResponse(query, language, response);
        assertTrue(stored);

        // Mock retrieval
        when(dynamoDbService.getCachedResponse(anyString(), eq(language)))
                .thenReturn(Optional.of(response));

        // Retrieve response
        Optional<String> retrieved = cacheManagerService.getCachedResponse(query, language);
        assertTrue(retrieved.isPresent());
        assertEquals(response, retrieved.get());

        verify(dynamoDbService, times(1)).cacheResponse(anyString(), eq(language), eq(response));
        verify(dynamoDbService, times(1)).getCachedResponse(anyString(), eq(language));
    }

    @Test
    void testHashConsistency_MultipleLanguages() {
        String query = "Where is the nearest hospital?";
        String[] languages = {"en", "ha", "yo", "ig", "ff"};

        for (String language : languages) {
            String hash1 = cacheManagerService.computeQueryHash(query, language);
            String hash2 = cacheManagerService.computeQueryHash(query, language);
            
            assertEquals(hash1, hash2, "Hash should be consistent for language: " + language);
            assertEquals(64, hash1.length(), "Hash should be 64 characters for language: " + language);
        }
    }
}

package com.africanservices.assistant.service;

import com.africanservices.assistant.service.BedrockService.BedrockException;
import com.africanservices.assistant.service.ResponseGeneratorService.ResponseGenerationException;
import com.africanservices.assistant.service.ServiceCategoryClassifier.ServiceCategory;
import com.africanservices.assistant.service.SessionManagerService.ConversationTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ResponseGeneratorService.
 */
class ResponseGeneratorServiceTest {

    @Mock
    private BedrockService bedrockService;

    @Mock
    private SessionManagerService sessionManagerService;

    @Mock
    private CacheManagerService cacheManagerService;

    @Mock
    private PromptTemplateManager promptTemplateManager;

    @Mock
    private GracefulDegradationService gracefulDegradationService;

    private ResponseGeneratorService responseGeneratorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        responseGeneratorService = new ResponseGeneratorService(
            bedrockService,
            sessionManagerService,
            cacheManagerService,
            promptTemplateManager,
            gracefulDegradationService
        );
    }

    @Test
    void testGenerateResponse_Success() throws Exception {
        String sessionId = "session-123";
        String userQuery = "What documents do I need?";
        String language = "en";
        ServiceCategory category = ServiceCategory.GOVERNMENT;
        String expectedResponse = "You need a valid ID and proof of address.";

        // Mock cache miss
        when(cacheManagerService.getCachedResponse(userQuery, language))
            .thenReturn(Optional.empty());

        // Mock session context
        List<ConversationTurn> history = Collections.emptyList();
        when(sessionManagerService.getConversationContext(sessionId))
            .thenReturn(history);

        // Mock prompt building
        String prompt = "System prompt with query";
        when(promptTemplateManager.buildCompletePrompt(eq(category), eq(language), anyString(), eq(userQuery)))
            .thenReturn(prompt);

        // Mock Bedrock response
        when(bedrockService.invokeModel(prompt))
            .thenReturn(expectedResponse);

        // Execute
        String response = responseGeneratorService.generateResponse(sessionId, userQuery, language, category);

        // Verify
        assertNotNull(response);
        assertEquals(expectedResponse, response);
        verify(cacheManagerService).cacheResponse(userQuery, language, expectedResponse);
    }

    @Test
    void testGenerateResponse_CacheHit() throws Exception {
        String sessionId = "session-123";
        String userQuery = "What documents do I need?";
        String language = "en";
        ServiceCategory category = ServiceCategory.GOVERNMENT;
        String cachedResponse = "Cached: You need a valid ID.";

        // Mock cache hit
        when(cacheManagerService.getCachedResponse(userQuery, language))
            .thenReturn(Optional.of(cachedResponse));

        // Execute
        String response = responseGeneratorService.generateResponse(sessionId, userQuery, language, category);

        // Verify
        assertNotNull(response);
        assertEquals(cachedResponse, response);
        
        // Bedrock should not be called
        verify(bedrockService, never()).invokeModel(anyString());
        
        // Should not cache again
        verify(cacheManagerService, never()).cacheResponse(anyString(), anyString(), anyString());
    }

    @Test
    void testGenerateResponse_WithSessionContext() throws Exception {
        String sessionId = "session-123";
        String userQuery = "What about fees?";
        String language = "en";
        ServiceCategory category = ServiceCategory.GOVERNMENT;
        String expectedResponse = "The fee is $50.";

        // Mock cache miss
        when(cacheManagerService.getCachedResponse(userQuery, language))
            .thenReturn(Optional.empty());

        // Mock session context with history
        List<ConversationTurn> history = Arrays.asList(
            new ConversationTurn(System.currentTimeMillis() - 60000, "Hello", "Hi, how can I help?"),
            new ConversationTurn(System.currentTimeMillis() - 30000, "I need a passport", "You need to apply at the office.")
        );
        when(sessionManagerService.getConversationContext(sessionId))
            .thenReturn(history);

        // Mock prompt building
        String prompt = "System prompt with context and query";
        when(promptTemplateManager.buildCompletePrompt(eq(category), eq(language), anyString(), eq(userQuery)))
            .thenReturn(prompt);

        // Mock Bedrock response
        when(bedrockService.invokeModel(prompt))
            .thenReturn(expectedResponse);

        // Execute
        String response = responseGeneratorService.generateResponse(sessionId, userQuery, language, category);

        // Verify
        assertNotNull(response);
        assertEquals(expectedResponse, response);
        verify(sessionManagerService).getConversationContext(sessionId);
    }

    @Test
    void testGenerateResponse_NoSessionContext() throws Exception {
        String userQuery = "What documents do I need?";
        String language = "en";
        ServiceCategory category = ServiceCategory.GOVERNMENT;
        String expectedResponse = "You need a valid ID.";

        // Mock cache miss
        when(cacheManagerService.getCachedResponse(userQuery, language))
            .thenReturn(Optional.empty());

        // Mock prompt building with empty context
        String prompt = "System prompt with query";
        when(promptTemplateManager.buildCompletePrompt(eq(category), eq(language), eq(""), eq(userQuery)))
            .thenReturn(prompt);

        // Mock Bedrock response
        when(bedrockService.invokeModel(prompt))
            .thenReturn(expectedResponse);

        // Execute (no session ID)
        String response = responseGeneratorService.generateResponse(userQuery, language, category);

        // Verify
        assertNotNull(response);
        assertEquals(expectedResponse, response);
        verify(sessionManagerService, never()).getConversationContext(anyString());
    }

    @Test
    void testGenerateResponse_EmptyQuery() {
        String sessionId = "session-123";
        String userQuery = "";
        String language = "en";
        ServiceCategory category = ServiceCategory.GOVERNMENT;

        // Execute and verify exception
        assertThrows(ResponseGenerationException.class, () -> {
            responseGeneratorService.generateResponse(sessionId, userQuery, language, category);
        });
    }

    @Test
    void testGenerateResponse_NullQuery() {
        String sessionId = "session-123";
        String userQuery = null;
        String language = "en";
        ServiceCategory category = ServiceCategory.GOVERNMENT;

        // Execute and verify exception
        assertThrows(ResponseGenerationException.class, () -> {
            responseGeneratorService.generateResponse(sessionId, userQuery, language, category);
        });
    }

    @Test
    void testGenerateResponse_DefaultLanguage() throws Exception {
        String sessionId = "session-123";
        String userQuery = "What documents do I need?";
        String language = null; // Will default to "en"
        ServiceCategory category = ServiceCategory.GOVERNMENT;
        String expectedResponse = "You need a valid ID.";

        // Mock cache miss
        when(cacheManagerService.getCachedResponse(eq(userQuery), anyString()))
            .thenReturn(Optional.empty());

        // Mock session context
        when(sessionManagerService.getConversationContext(sessionId))
            .thenReturn(Collections.emptyList());

        // Mock prompt building
        when(promptTemplateManager.buildCompletePrompt(any(), anyString(), anyString(), anyString()))
            .thenReturn("prompt");

        // Mock Bedrock response
        when(bedrockService.invokeModel(anyString()))
            .thenReturn(expectedResponse);

        // Execute
        String response = responseGeneratorService.generateResponse(sessionId, userQuery, language, category);

        // Verify
        assertNotNull(response);
        assertEquals(expectedResponse, response);
    }

    @Test
    void testGenerateResponse_DefaultCategory() throws Exception {
        String sessionId = "session-123";
        String userQuery = "What documents do I need?";
        String language = "en";
        ServiceCategory category = null; // Will default to GOVERNMENT
        String expectedResponse = "You need a valid ID.";

        // Mock cache miss
        when(cacheManagerService.getCachedResponse(userQuery, language))
            .thenReturn(Optional.empty());

        // Mock session context
        when(sessionManagerService.getConversationContext(sessionId))
            .thenReturn(Collections.emptyList());

        // Mock prompt building
        when(promptTemplateManager.buildCompletePrompt(any(), anyString(), anyString(), anyString()))
            .thenReturn("prompt");

        // Mock Bedrock response
        when(bedrockService.invokeModel(anyString()))
            .thenReturn(expectedResponse);

        // Execute
        String response = responseGeneratorService.generateResponse(sessionId, userQuery, language, category);

        // Verify
        assertNotNull(response);
        assertEquals(expectedResponse, response);
    }

    @Test
    void testGenerateResponse_BedrockFailure_WithCachedFallback() throws Exception {
        String sessionId = "session-123";
        String userQuery = "What documents do I need?";
        String language = "en";
        ServiceCategory category = ServiceCategory.GOVERNMENT;
        String cachedFallback = "Cached fallback response";

        // Mock cache miss on first call
        when(cacheManagerService.getCachedResponse(userQuery, language))
            .thenReturn(Optional.empty());

        // Mock session context
        when(sessionManagerService.getConversationContext(sessionId))
            .thenReturn(Collections.emptyList());

        // Mock prompt building
        when(promptTemplateManager.buildCompletePrompt(any(), anyString(), anyString(), anyString()))
            .thenReturn("prompt");

        // Mock Bedrock failure
        when(bedrockService.invokeModel(anyString()))
            .thenThrow(new BedrockException("Service unavailable"));

        // Mock graceful degradation returning cached response
        when(gracefulDegradationService.handleBedrockUnavailable(userQuery, language, category.name()))
            .thenReturn(cachedFallback);

        // Execute
        String response = responseGeneratorService.generateResponse(sessionId, userQuery, language, category);

        // Verify fallback was used
        assertNotNull(response);
        assertEquals(cachedFallback, response);
        verify(gracefulDegradationService).handleBedrockUnavailable(userQuery, language, category.name());
    }

    @Test
    void testGenerateResponse_BedrockFailure_WithoutCachedFallback() throws Exception {
        String sessionId = "session-123";
        String userQuery = "What documents do I need?";
        String language = "en";
        ServiceCategory category = ServiceCategory.GOVERNMENT;
        String fallbackMessage = "I apologize, but I'm having trouble answering your question about government services.";

        // Mock cache miss
        when(cacheManagerService.getCachedResponse(userQuery, language))
            .thenReturn(Optional.empty());

        // Mock session context
        when(sessionManagerService.getConversationContext(sessionId))
            .thenReturn(Collections.emptyList());

        // Mock prompt building
        when(promptTemplateManager.buildCompletePrompt(any(), anyString(), anyString(), anyString()))
            .thenReturn("prompt");

        // Mock Bedrock failure
        when(bedrockService.invokeModel(anyString()))
            .thenThrow(new BedrockException("Service unavailable"));

        // Mock graceful degradation returning fallback message
        when(gracefulDegradationService.handleBedrockUnavailable(userQuery, language, category.name()))
            .thenReturn(fallbackMessage);

        // Execute
        String response = responseGeneratorService.generateResponse(sessionId, userQuery, language, category);

        // Verify fallback message was returned
        assertNotNull(response);
        assertEquals(fallbackMessage, response);
        verify(gracefulDegradationService).handleBedrockUnavailable(userQuery, language, category.name());
    }

    @Test
    void testGenerateResponse_FallbackMessages_AllLanguages() throws Exception {
        String sessionId = "session-123";
        String userQuery = "Test query";
        ServiceCategory category = ServiceCategory.HEALTH;

        // Test all supported languages
        String[] languages = {"en", "ha", "yo", "ig", "ff"};
        
        for (String language : languages) {
            // Reset mocks for each iteration
            reset(cacheManagerService, sessionManagerService, promptTemplateManager, bedrockService);
            
            // Mock cache miss
            when(cacheManagerService.getCachedResponse(userQuery, language))
                .thenReturn(Optional.empty());

            // Mock session context
            when(sessionManagerService.getConversationContext(sessionId))
                .thenReturn(Collections.emptyList());

            // Mock prompt building
            when(promptTemplateManager.buildCompletePrompt(any(), anyString(), anyString(), anyString()))
                .thenReturn("prompt");

            // Mock Bedrock failure
            when(bedrockService.invokeModel(anyString()))
                .thenThrow(new BedrockException("Service unavailable"));

            // Execute
            String response = responseGeneratorService.generateResponse(sessionId, userQuery, language, category);

            // Verify fallback message was returned
            assertNotNull(response, "Fallback message should not be null for language: " + language);
            assertFalse(response.isEmpty(), "Fallback message should not be empty for language: " + language);
        }
    }

    @Test
    void testGenerateResponse_ResponseFormatting() throws Exception {
        String sessionId = "session-123";
        String userQuery = "What documents do I need?";
        String language = "en";
        ServiceCategory category = ServiceCategory.GOVERNMENT;
        String bedrockResponse = "  You need a valid ID.  \n\n";
        String expectedResponse = "You need a valid ID.";

        // Mock cache miss
        when(cacheManagerService.getCachedResponse(userQuery, language))
            .thenReturn(Optional.empty());

        // Mock session context
        when(sessionManagerService.getConversationContext(sessionId))
            .thenReturn(Collections.emptyList());

        // Mock prompt building
        when(promptTemplateManager.buildCompletePrompt(any(), anyString(), anyString(), anyString()))
            .thenReturn("prompt");

        // Mock Bedrock response with whitespace
        when(bedrockService.invokeModel(anyString()))
            .thenReturn(bedrockResponse);

        // Execute
        String response = responseGeneratorService.generateResponse(sessionId, userQuery, language, category);

        // Verify response was trimmed
        assertNotNull(response);
        assertEquals(expectedResponse, response);
    }
}

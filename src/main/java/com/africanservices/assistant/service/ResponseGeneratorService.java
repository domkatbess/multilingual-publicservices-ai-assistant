package com.africanservices.assistant.service;

import com.africanservices.assistant.service.BedrockService.BedrockException;
import com.africanservices.assistant.service.ServiceCategoryClassifier.ServiceCategory;
import com.africanservices.assistant.service.SessionManagerService.ConversationTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Response generator service that orchestrates the response generation flow.
 * Integrates with Bedrock, Session, Cache, and Prompt Template services.
 * 
 * Validates Requirements: 4.1, 4.4, 4.5, 4.6
 */
@Service
public class ResponseGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(ResponseGeneratorService.class);
    
    private final BedrockService bedrockService;
    private final SessionManagerService sessionManagerService;
    private final CacheManagerService cacheManagerService;
    private final PromptTemplateManager promptTemplateManager;

    @Autowired
    public ResponseGeneratorService(
            BedrockService bedrockService,
            SessionManagerService sessionManagerService,
            CacheManagerService cacheManagerService,
            PromptTemplateManager promptTemplateManager) {
        this.bedrockService = bedrockService;
        this.sessionManagerService = sessionManagerService;
        this.cacheManagerService = cacheManagerService;
        this.promptTemplateManager = promptTemplateManager;
    }

    /**
     * Generate a response for a user query with full context and caching.
     * 
     * Requirements 4.1, 4.4, 4.5, 4.6: Invoke Bedrock with context, format in target language,
     * maintain cultural context, implement graceful degradation
     * 
     * @param sessionId Session identifier
     * @param userQuery User's query text
     * @param language Target language for response
     * @param category Service category
     * @return Generated response text
     * @throws ResponseGenerationException if response generation fails
     */
    public String generateResponse(String sessionId, String userQuery, 
                                   String language, ServiceCategory category) 
            throws ResponseGenerationException {
        
        if (userQuery == null || userQuery.trim().isEmpty()) {
            throw new ResponseGenerationException("User query cannot be empty");
        }
        
        if (language == null || language.trim().isEmpty()) {
            logger.warn("Language not specified, defaulting to English");
            language = "en";
        }
        
        if (category == null) {
            logger.warn("Category not specified, defaulting to GOVERNMENT");
            category = ServiceCategory.GOVERNMENT;
        }
        
        logger.info("Generating response: sessionId={}, language={}, category={}", 
                   sessionId, language, category);
        
        try {
            // Check cache first
            Optional<String> cachedResponse = cacheManagerService.getCachedResponse(userQuery, language);
            if (cachedResponse.isPresent()) {
                logger.info("Returning cached response for query");
                return cachedResponse.get();
            }
            
            // Build context from session history
            String context = buildContextFromSession(sessionId);
            
            // Construct complete prompt
            String prompt = promptTemplateManager.buildCompletePrompt(
                category, 
                language, 
                context, 
                userQuery
            );
            
            // Invoke Bedrock to generate response
            String response = bedrockService.invokeModel(prompt);
            
            // Format response in target language (already done by Bedrock with proper prompt)
            String formattedResponse = formatResponse(response, language);
            
            // Cache the response
            cacheManagerService.cacheResponse(userQuery, language, formattedResponse);
            
            logger.info("Successfully generated response: length={}", formattedResponse.length());
            return formattedResponse;
            
        } catch (BedrockException e) {
            logger.error("Bedrock invocation failed: {}", e.getMessage(), e);
            // Graceful degradation: try to return a fallback response
            return handleBedrockFailure(userQuery, language, category);
        } catch (Exception e) {
            logger.error("Unexpected error during response generation: {}", e.getMessage(), e);
            throw new ResponseGenerationException("Failed to generate response", e);
        }
    }

    /**
     * Generate a response without session context (for stateless queries).
     * 
     * @param userQuery User's query text
     * @param language Target language for response
     * @param category Service category
     * @return Generated response text
     * @throws ResponseGenerationException if response generation fails
     */
    public String generateResponse(String userQuery, String language, ServiceCategory category) 
            throws ResponseGenerationException {
        return generateResponse(null, userQuery, language, category);
    }

    /**
     * Build conversation context from session history.
     * Retrieves last 5 turns and formats them for inclusion in the prompt.
     * 
     * @param sessionId Session identifier
     * @return Formatted context string, or empty string if no session
     */
    private String buildContextFromSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return "";
        }
        
        try {
            List<ConversationTurn> history = sessionManagerService.getConversationContext(sessionId);
            
            if (history.isEmpty()) {
                return "";
            }
            
            StringBuilder contextBuilder = new StringBuilder();
            for (ConversationTurn turn : history) {
                contextBuilder.append("User: ").append(turn.getUserMessage()).append("\n");
                contextBuilder.append("Assistant: ").append(turn.getAssistantResponse()).append("\n");
            }
            
            return contextBuilder.toString().trim();
            
        } catch (Exception e) {
            logger.warn("Failed to retrieve session context: sessionId={}, error={}", 
                       sessionId, e.getMessage());
            return "";
        }
    }

    /**
     * Format the response for the target language.
     * Currently a pass-through as Bedrock handles language formatting via prompts.
     * Can be extended for post-processing if needed.
     * 
     * Requirement 4.4: Format response in target language
     * 
     * @param response Raw response from Bedrock
     * @param language Target language
     * @return Formatted response
     */
    private String formatResponse(String response, String language) {
        if (response == null) {
            return "";
        }
        
        // Trim whitespace
        String formatted = response.trim();
        
        // Future: Add language-specific formatting rules here if needed
        // For now, Bedrock handles language formatting via system prompts
        
        return formatted;
    }

    /**
     * Handle Bedrock service failure with graceful degradation.
     * Returns a cached response or fallback message in the user's language.
     * 
     * Requirement 4.6: Implement graceful degradation (cached/fallback responses)
     * 
     * @param userQuery User's query
     * @param language Target language
     * @param category Service category
     * @return Fallback response
     */
    private String handleBedrockFailure(String userQuery, String language, ServiceCategory category) {
        logger.warn("Attempting graceful degradation for Bedrock failure");
        
        // Try to get a cached response first
        Optional<String> cachedResponse = cacheManagerService.getCachedResponse(userQuery, language);
        if (cachedResponse.isPresent()) {
            logger.info("Returning cached response as fallback");
            return cachedResponse.get();
        }
        
        // Return a fallback message in the user's language
        String fallbackMessage = getFallbackMessage(language, category);
        logger.info("Returning fallback message");
        return fallbackMessage;
    }

    /**
     * Get a fallback message in the specified language.
     * 
     * @param language Target language
     * @param category Service category
     * @return Fallback message
     */
    private String getFallbackMessage(String language, ServiceCategory category) {
        // Map language codes to fallback messages
        String categoryName = getCategoryName(category, language);
        
        switch (language.toLowerCase()) {
            case "ha": // Hausa
                return "Yi hakuri, ina da matsala wajen amsa tambayarka game da " + categoryName + 
                       ". Don Allah sake gwadawa daga baya ko tuntuɓi hukumar da ta dace.";
            
            case "yo": // Yoruba
                return "Jọwọ, Mo ni iṣoro lati dahun ibeere rẹ nipa " + categoryName + 
                       ". Jọwọ gbiyanju lẹẹkansi tabi kan si awọn alaṣẹ to yẹ.";
            
            case "ig": // Igbo
                return "Biko, Enwere m nsogbu ịza ajụjụ gị banyere " + categoryName + 
                       ". Biko nwalee ọzọ ma ọ bụ kpọtụrụ ndị ọrụ kwesịrị ekwesị.";
            
            case "ff": // Fulfulde
                return "Yaafoore, Mi jogii e jaabde naamndal maa e " + categoryName + 
                       ". Tiiɗno eɗen kadi walla jokkondire e jaaynooɓe ɓurɗe.";
            
            case "en": // English
            default:
                return "I apologize, but I'm having trouble answering your question about " + categoryName + 
                       ". Please try again later or contact the appropriate authorities.";
        }
    }

    /**
     * Get the category name in the specified language.
     * 
     * @param category Service category
     * @param language Target language
     * @return Category name
     */
    private String getCategoryName(ServiceCategory category, String language) {
        if (category == null) {
            category = ServiceCategory.GOVERNMENT;
        }
        
        // For simplicity, return English category names
        // Future: Add translations for all supported languages
        switch (category) {
            case GOVERNMENT:
                return "government services";
            case HEALTH:
                return "health services";
            case EDUCATION:
                return "education services";
            case EMERGENCY:
                return "emergency services";
            default:
                return "public services";
        }
    }

    /**
     * Custom exception for response generation errors.
     */
    public static class ResponseGenerationException extends Exception {
        public ResponseGenerationException(String message) {
            super(message);
        }

        public ResponseGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

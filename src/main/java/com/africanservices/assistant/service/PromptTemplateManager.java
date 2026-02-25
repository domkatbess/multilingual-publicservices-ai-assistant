package com.africanservices.assistant.service;

import com.africanservices.assistant.service.ServiceCategoryClassifier.ServiceCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Prompt template manager for generating category-specific system prompts.
 * Provides templates tailored to Government, Health, Education, and Emergency services.
 * 
 * Validates Requirements: 4.2, 8.2, 8.3, 8.4, 8.5
 */
@Service
public class PromptTemplateManager {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateManager.class);
    
    // Template placeholders
    private static final String LANGUAGE_PLACEHOLDER = "{LANGUAGE}";
    private static final String SERVICE_CATEGORY_PLACEHOLDER = "{SERVICE_CATEGORY}";
    
    // Base system prompt template
    private static final String BASE_TEMPLATE = 
        "You are a helpful assistant for public services in " + LANGUAGE_PLACEHOLDER + ".\n" +
        "You provide accurate, culturally appropriate information about " + SERVICE_CATEGORY_PLACEHOLDER + ".\n" +
        "Keep responses clear, concise, and actionable.\n" +
        "If you don't know something, direct users to appropriate authorities.\n" +
        "Always be respectful and patient.\n\n";
    
    // Category-specific guidance templates
    private static final Map<ServiceCategory, String> CATEGORY_TEMPLATES = new HashMap<>();
    
    static {
        // Government services: focus on documents, procedures, eligibility
        CATEGORY_TEMPLATES.put(ServiceCategory.GOVERNMENT,
            "Focus on:\n" +
            "- Required documents and identification\n" +
            "- Application procedures and steps\n" +
            "- Eligibility criteria and requirements\n" +
            "- Government office locations and hours\n" +
            "- Processing times and fees\n" +
            "- Appeal and complaint procedures\n\n" +
            "Provide specific, actionable guidance on navigating government services."
        );
        
        // Health services: focus on facilities, education, non-diagnostic guidance
        CATEGORY_TEMPLATES.put(ServiceCategory.HEALTH,
            "Focus on:\n" +
            "- Healthcare facility locations and services\n" +
            "- Health education and preventive care\n" +
            "- Vaccination schedules and immunization programs\n" +
            "- General wellness and nutrition guidance\n" +
            "- When to seek medical attention\n\n" +
            "IMPORTANT: Do NOT provide medical diagnoses or treatment recommendations.\n" +
            "Always advise users to consult qualified healthcare professionals for medical concerns.\n" +
            "Provide general health education and facility information only."
        );
        
        // Education services: focus on enrollment, scholarships, resources
        CATEGORY_TEMPLATES.put(ServiceCategory.EDUCATION,
            "Focus on:\n" +
            "- School enrollment procedures and requirements\n" +
            "- Scholarship opportunities and application processes\n" +
            "- Educational resources and learning materials\n" +
            "- Literacy programs and adult education\n" +
            "- School locations and contact information\n" +
            "- Academic calendars and important dates\n\n" +
            "Help users access educational opportunities and resources."
        );
        
        // Emergency services: focus on contact numbers, first aid, reporting
        CATEGORY_TEMPLATES.put(ServiceCategory.EMERGENCY,
            "Focus on:\n" +
            "- Emergency contact numbers (police, fire, ambulance)\n" +
            "- First aid guidance and immediate response steps\n" +
            "- Disaster preparedness and safety procedures\n" +
            "- How to report emergencies and incidents\n" +
            "- Evacuation procedures and safe locations\n\n" +
            "URGENT: For life-threatening emergencies, always direct users to call emergency services immediately.\n" +
            "Provide clear, step-by-step guidance for emergency situations."
        );
    }

    /**
     * Get the system prompt for a specific service category and language.
     * 
     * Requirement 4.2: Include system prompt appropriate for detected service category
     * Requirements 8.2-8.5: Category-specific prompt selection
     * 
     * @param category Service category
     * @param language Language code or name
     * @return Complete system prompt with category-specific guidance
     */
    public String getSystemPrompt(ServiceCategory category, String language) {
        if (category == null) {
            logger.warn("Null category provided, defaulting to GOVERNMENT");
            category = ServiceCategory.GOVERNMENT;
        }
        
        if (language == null || language.trim().isEmpty()) {
            logger.warn("Empty language provided, defaulting to 'the user's language'");
            language = "the user's language";
        }
        
        String categoryGuidance = CATEGORY_TEMPLATES.getOrDefault(
            category, 
            CATEGORY_TEMPLATES.get(ServiceCategory.GOVERNMENT)
        );
        
        String prompt = BASE_TEMPLATE
            .replace(LANGUAGE_PLACEHOLDER, formatLanguageName(language))
            .replace(SERVICE_CATEGORY_PLACEHOLDER, formatCategoryName(category));
        
        prompt += categoryGuidance;
        
        logger.debug("Generated system prompt for category={}, language={}", category, language);
        return prompt;
    }

    /**
     * Get the system prompt with default language placeholder.
     * 
     * @param category Service category
     * @return System prompt with generic language reference
     */
    public String getSystemPrompt(ServiceCategory category) {
        return getSystemPrompt(category, "the user's language");
    }

    /**
     * Build a complete prompt with system instructions, context, and user query.
     * 
     * @param category Service category
     * @param language Language code or name
     * @param context Conversation context (previous turns)
     * @param userQuery Current user query
     * @return Complete prompt ready for Bedrock invocation
     */
    public String buildCompletePrompt(ServiceCategory category, String language, 
                                     String context, String userQuery) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // Add system prompt
        promptBuilder.append(getSystemPrompt(category, language));
        promptBuilder.append("\n");
        
        // Add conversation context if available
        if (context != null && !context.trim().isEmpty()) {
            promptBuilder.append("Previous conversation:\n");
            promptBuilder.append(context);
            promptBuilder.append("\n\n");
        }
        
        // Add current user query
        promptBuilder.append("User query: ");
        promptBuilder.append(userQuery);
        promptBuilder.append("\n\n");
        promptBuilder.append("Response:");
        
        return promptBuilder.toString();
    }

    /**
     * Format language code or name for display in prompts.
     * Converts language codes to full names where possible.
     * 
     * @param language Language code or name
     * @return Formatted language name
     */
    private String formatLanguageName(String language) {
        if (language == null || language.trim().isEmpty()) {
            return "the user's language";
        }
        
        String lang = language.trim().toLowerCase();
        
        // Map common language codes to full names
        switch (lang) {
            case "ha":
                return "Hausa";
            case "yo":
                return "Yoruba";
            case "ig":
                return "Igbo";
            case "ff":
                return "Fulfulde";
            case "en":
                return "English";
            default:
                // Return as-is if not a recognized code
                return language;
        }
    }

    /**
     * Format service category for display in prompts.
     * 
     * @param category Service category
     * @return Formatted category name
     */
    private String formatCategoryName(ServiceCategory category) {
        if (category == null) {
            return "public services";
        }
        
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
     * Get category-specific keywords for validation and testing.
     * 
     * @param category Service category
     * @return Array of keywords that should appear in the category's prompt
     */
    public String[] getCategoryKeywords(ServiceCategory category) {
        if (category == null) {
            return new String[0];
        }
        
        switch (category) {
            case GOVERNMENT:
                return new String[]{"documents", "procedures", "eligibility"};
            case HEALTH:
                return new String[]{"facilities", "education", "non-diagnostic"};
            case EDUCATION:
                return new String[]{"enrollment", "scholarships", "resources"};
            case EMERGENCY:
                return new String[]{"contact", "first aid", "reporting"};
            default:
                return new String[0];
        }
    }
}

package com.africanservices.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for classifying user queries into service categories.
 * Uses Amazon Bedrock to determine if a query relates to Government, Health, Education, or Emergency services.
 */
@Service
public class ServiceCategoryClassifier {

    private static final Logger logger = LoggerFactory.getLogger(ServiceCategoryClassifier.class);
    
    // Service categories
    public enum ServiceCategory {
        GOVERNMENT,
        HEALTH,
        EDUCATION,
        EMERGENCY
    }
    
    private static final ServiceCategory DEFAULT_CATEGORY = ServiceCategory.GOVERNMENT;
    
    private final BedrockService bedrockService;

    @Autowired
    public ServiceCategoryClassifier(BedrockService bedrockService) {
        this.bedrockService = bedrockService;
    }

    /**
     * Classifies a query into one of the four service categories.
     *
     * @param query The user query to classify
     * @return The service category (Government, Health, Education, or Emergency)
     */
    public ServiceCategory classifyQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            logger.warn("Empty query provided for classification, defaulting to Government");
            return DEFAULT_CATEGORY;
        }

        try {
            logger.debug("Classifying query: {}", query.substring(0, Math.min(50, query.length())));
            
            String prompt = buildClassificationPrompt(query);
            String response = bedrockService.invokeModel(prompt);
            
            ServiceCategory category = parseCategoryFromResponse(response);
            
            logger.info("Classified query as: {}", category);
            return category;
            
        } catch (Exception e) {
            logger.error("Classification failed: {}", e.getMessage(), e);
            return DEFAULT_CATEGORY;
        }
    }

    /**
     * Gets all available service categories.
     *
     * @return Set of all service categories
     */
    public Set<ServiceCategory> getServiceCategories() {
        return new HashSet<>(Arrays.asList(ServiceCategory.values()));
    }

    /**
     * Builds the prompt for service category classification.
     */
    private String buildClassificationPrompt(String query) {
        return String.format(
                "Classify the following query into exactly ONE of these service categories:\n" +
                "- GOVERNMENT: Questions about documents, procedures, applications, eligibility, government offices\n" +
                "- HEALTH: Questions about medical facilities, symptoms, health education, vaccinations, healthcare\n" +
                "- EDUCATION: Questions about school enrollment, scholarships, educational resources, literacy programs\n" +
                "- EMERGENCY: Questions about emergencies, first aid, disaster preparedness, emergency contacts, urgent help\n\n" +
                "Respond with ONLY the category name (GOVERNMENT, HEALTH, EDUCATION, or EMERGENCY). " +
                "Do not provide any explanation, just the category name.\n\n" +
                "Query: \"%s\"\n\n" +
                "Category:",
                query.trim()
        );
    }

    /**
     * Parses the service category from Bedrock's response.
     */
    private ServiceCategory parseCategoryFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return DEFAULT_CATEGORY;
        }

        // Clean up the response
        String cleaned = response.trim().toUpperCase();
        
        // Try to match exact category names
        if (cleaned.contains("GOVERNMENT")) {
            return ServiceCategory.GOVERNMENT;
        } else if (cleaned.contains("HEALTH")) {
            return ServiceCategory.HEALTH;
        } else if (cleaned.contains("EDUCATION")) {
            return ServiceCategory.EDUCATION;
        } else if (cleaned.contains("EMERGENCY")) {
            return ServiceCategory.EMERGENCY;
        }
        
        // Try to extract a single word that matches a category
        Pattern pattern = Pattern.compile("\\b(GOVERNMENT|HEALTH|EDUCATION|EMERGENCY)\\b");
        Matcher matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
            String match = matcher.group(1);
            return ServiceCategory.valueOf(match);
        }
        
        logger.warn("Could not parse category from response: {}, defaulting to Government", response);
        return DEFAULT_CATEGORY;
    }
}

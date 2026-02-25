package com.africanservices.assistant.service;

import com.africanservices.assistant.service.ServiceCategoryClassifier.ServiceCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PromptTemplateManager.
 */
class PromptTemplateManagerTest {

    private PromptTemplateManager promptTemplateManager;

    @BeforeEach
    void setUp() {
        promptTemplateManager = new PromptTemplateManager();
    }

    @Test
    void testGetSystemPrompt_Government() {
        String prompt = promptTemplateManager.getSystemPrompt(ServiceCategory.GOVERNMENT, "English");
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("English"));
        assertTrue(prompt.contains("government services"));
        assertTrue(prompt.toLowerCase().contains("documents"));
        assertTrue(prompt.toLowerCase().contains("procedures"));
        assertTrue(prompt.toLowerCase().contains("eligibility"));
    }

    @Test
    void testGetSystemPrompt_Health() {
        String prompt = promptTemplateManager.getSystemPrompt(ServiceCategory.HEALTH, "Hausa");
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("Hausa"));
        assertTrue(prompt.contains("health services"));
        assertTrue(prompt.toLowerCase().contains("healthcare facility"));
        assertTrue(prompt.toLowerCase().contains("health education"));
        assertTrue(prompt.contains("Do NOT provide medical diagnoses"));
    }

    @Test
    void testGetSystemPrompt_Education() {
        String prompt = promptTemplateManager.getSystemPrompt(ServiceCategory.EDUCATION, "Yoruba");
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("Yoruba"));
        assertTrue(prompt.contains("education services"));
        assertTrue(prompt.toLowerCase().contains("school enrollment"));
        assertTrue(prompt.toLowerCase().contains("scholarship"));
        assertTrue(prompt.toLowerCase().contains("educational resources"));
    }

    @Test
    void testGetSystemPrompt_Emergency() {
        String prompt = promptTemplateManager.getSystemPrompt(ServiceCategory.EMERGENCY, "Igbo");
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("Igbo"));
        assertTrue(prompt.contains("emergency services"));
        assertTrue(prompt.toLowerCase().contains("emergency contact"));
        assertTrue(prompt.toLowerCase().contains("first aid"));
        assertTrue(prompt.toLowerCase().contains("disaster preparedness"));
    }

    @Test
    void testGetSystemPrompt_LanguageCodeConversion() {
        String promptHa = promptTemplateManager.getSystemPrompt(ServiceCategory.GOVERNMENT, "ha");
        assertTrue(promptHa.contains("Hausa"));
        
        String promptYo = promptTemplateManager.getSystemPrompt(ServiceCategory.GOVERNMENT, "yo");
        assertTrue(promptYo.contains("Yoruba"));
        
        String promptIg = promptTemplateManager.getSystemPrompt(ServiceCategory.GOVERNMENT, "ig");
        assertTrue(promptIg.contains("Igbo"));
        
        String promptFf = promptTemplateManager.getSystemPrompt(ServiceCategory.GOVERNMENT, "ff");
        assertTrue(promptFf.contains("Fulfulde"));
        
        String promptEn = promptTemplateManager.getSystemPrompt(ServiceCategory.GOVERNMENT, "en");
        assertTrue(promptEn.contains("English"));
    }

    @Test
    void testGetSystemPrompt_NullCategory() {
        String prompt = promptTemplateManager.getSystemPrompt(null, "English");
        
        assertNotNull(prompt);
        // Should default to GOVERNMENT
        assertTrue(prompt.contains("documents"));
        assertTrue(prompt.contains("procedures"));
    }

    @Test
    void testGetSystemPrompt_NullLanguage() {
        String prompt = promptTemplateManager.getSystemPrompt(ServiceCategory.GOVERNMENT, null);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("the user's language"));
    }

    @Test
    void testGetSystemPrompt_EmptyLanguage() {
        String prompt = promptTemplateManager.getSystemPrompt(ServiceCategory.GOVERNMENT, "");
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("the user's language"));
    }

    @Test
    void testGetSystemPrompt_WithoutLanguage() {
        String prompt = promptTemplateManager.getSystemPrompt(ServiceCategory.HEALTH);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("the user's language"));
        assertTrue(prompt.contains("health services"));
    }

    @Test
    void testBuildCompletePrompt_WithContext() {
        String context = "User: Hello\nAssistant: Hi, how can I help you?";
        String userQuery = "What documents do I need?";
        
        String prompt = promptTemplateManager.buildCompletePrompt(
            ServiceCategory.GOVERNMENT, 
            "English", 
            context, 
            userQuery
        );
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("government services"));
        assertTrue(prompt.contains("Previous conversation:"));
        assertTrue(prompt.contains(context));
        assertTrue(prompt.contains("User query: " + userQuery));
        assertTrue(prompt.contains("Response:"));
    }

    @Test
    void testBuildCompletePrompt_WithoutContext() {
        String userQuery = "Where is the nearest hospital?";
        
        String prompt = promptTemplateManager.buildCompletePrompt(
            ServiceCategory.HEALTH, 
            "Hausa", 
            null, 
            userQuery
        );
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("health services"));
        assertFalse(prompt.contains("Previous conversation:"));
        assertTrue(prompt.contains("User query: " + userQuery));
        assertTrue(prompt.contains("Response:"));
    }

    @Test
    void testBuildCompletePrompt_EmptyContext() {
        String userQuery = "How do I enroll my child?";
        
        String prompt = promptTemplateManager.buildCompletePrompt(
            ServiceCategory.EDUCATION, 
            "Yoruba", 
            "", 
            userQuery
        );
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("education services"));
        assertFalse(prompt.contains("Previous conversation:"));
        assertTrue(prompt.contains("User query: " + userQuery));
    }

    @Test
    void testGetCategoryKeywords_Government() {
        String[] keywords = promptTemplateManager.getCategoryKeywords(ServiceCategory.GOVERNMENT);
        
        assertNotNull(keywords);
        assertEquals(3, keywords.length);
        assertArrayEquals(new String[]{"documents", "procedures", "eligibility"}, keywords);
    }

    @Test
    void testGetCategoryKeywords_Health() {
        String[] keywords = promptTemplateManager.getCategoryKeywords(ServiceCategory.HEALTH);
        
        assertNotNull(keywords);
        assertEquals(3, keywords.length);
        assertArrayEquals(new String[]{"facilities", "education", "non-diagnostic"}, keywords);
    }

    @Test
    void testGetCategoryKeywords_Education() {
        String[] keywords = promptTemplateManager.getCategoryKeywords(ServiceCategory.EDUCATION);
        
        assertNotNull(keywords);
        assertEquals(3, keywords.length);
        assertArrayEquals(new String[]{"enrollment", "scholarships", "resources"}, keywords);
    }

    @Test
    void testGetCategoryKeywords_Emergency() {
        String[] keywords = promptTemplateManager.getCategoryKeywords(ServiceCategory.EMERGENCY);
        
        assertNotNull(keywords);
        assertEquals(3, keywords.length);
        assertArrayEquals(new String[]{"contact", "first aid", "reporting"}, keywords);
    }

    @Test
    void testGetCategoryKeywords_Null() {
        String[] keywords = promptTemplateManager.getCategoryKeywords(null);
        
        assertNotNull(keywords);
        assertEquals(0, keywords.length);
    }

    @Test
    void testPromptContainsBaseInstructions() {
        String prompt = promptTemplateManager.getSystemPrompt(ServiceCategory.GOVERNMENT, "English");
        
        assertTrue(prompt.contains("helpful assistant"));
        assertTrue(prompt.contains("accurate, culturally appropriate"));
        assertTrue(prompt.contains("clear, concise, and actionable"));
        assertTrue(prompt.contains("respectful and patient"));
    }

    @Test
    void testAllCategoriesHaveUniqueKeywords() {
        String govPrompt = promptTemplateManager.getSystemPrompt(ServiceCategory.GOVERNMENT, "en");
        String healthPrompt = promptTemplateManager.getSystemPrompt(ServiceCategory.HEALTH, "en");
        String eduPrompt = promptTemplateManager.getSystemPrompt(ServiceCategory.EDUCATION, "en");
        String emergPrompt = promptTemplateManager.getSystemPrompt(ServiceCategory.EMERGENCY, "en");
        
        // Government should have documents/procedures
        assertTrue(govPrompt.toLowerCase().contains("documents"));
        assertTrue(govPrompt.toLowerCase().contains("procedures"));
        
        // Health should have facilities and non-diagnostic warning
        assertTrue(healthPrompt.toLowerCase().contains("healthcare facility"));
        assertTrue(healthPrompt.contains("Do NOT provide medical diagnoses"));
        
        // Education should have school enrollment/scholarships
        assertTrue(eduPrompt.toLowerCase().contains("school enrollment"));
        assertTrue(eduPrompt.toLowerCase().contains("scholarship"));
        
        // Emergency should have emergency contact/first aid
        assertTrue(emergPrompt.toLowerCase().contains("emergency contact"));
        assertTrue(emergPrompt.toLowerCase().contains("first aid"));
    }
}

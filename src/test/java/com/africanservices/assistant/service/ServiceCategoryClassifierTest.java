package com.africanservices.assistant.service;

import com.africanservices.assistant.service.ServiceCategoryClassifier.ServiceCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceCategoryClassifierTest {

    @Mock
    private BedrockService bedrockService;

    private ServiceCategoryClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ServiceCategoryClassifier(bedrockService);
    }

    @Test
    void testClassifyQuery_Government() throws BedrockService.BedrockException {
        // Arrange
        String query = "How do I apply for a passport?";
        when(bedrockService.invokeModel(anyString())).thenReturn("GOVERNMENT");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.GOVERNMENT, result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testClassifyQuery_Health() throws BedrockService.BedrockException {
        // Arrange
        String query = "Where is the nearest hospital?";
        when(bedrockService.invokeModel(anyString())).thenReturn("HEALTH");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.HEALTH, result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testClassifyQuery_Education() throws BedrockService.BedrockException {
        // Arrange
        String query = "How can I enroll my child in school?";
        when(bedrockService.invokeModel(anyString())).thenReturn("EDUCATION");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.EDUCATION, result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testClassifyQuery_Emergency() throws BedrockService.BedrockException {
        // Arrange
        String query = "What is the emergency number?";
        when(bedrockService.invokeModel(anyString())).thenReturn("EMERGENCY");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.EMERGENCY, result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testClassifyQuery_ResponseWithExtraText() throws BedrockService.BedrockException {
        // Arrange
        String query = "Test query";
        when(bedrockService.invokeModel(anyString())).thenReturn("The category is HEALTH");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.HEALTH, result);
    }

    @Test
    void testClassifyQuery_ResponseWithLowerCase() throws BedrockService.BedrockException {
        // Arrange
        String query = "Test query";
        when(bedrockService.invokeModel(anyString())).thenReturn("education");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.EDUCATION, result);
    }

    @Test
    void testClassifyQuery_EmptyQuery_DefaultsToGovernment() throws BedrockService.BedrockException {
        // Act
        ServiceCategory result = classifier.classifyQuery("");

        // Assert
        assertEquals(ServiceCategory.GOVERNMENT, result);
        verify(bedrockService, never()).invokeModel(anyString());
    }

    @Test
    void testClassifyQuery_NullQuery_DefaultsToGovernment() throws BedrockService.BedrockException {
        // Act
        ServiceCategory result = classifier.classifyQuery(null);

        // Assert
        assertEquals(ServiceCategory.GOVERNMENT, result);
        verify(bedrockService, never()).invokeModel(anyString());
    }

    @Test
    void testClassifyQuery_BedrockFailure_DefaultsToGovernment() throws BedrockService.BedrockException {
        // Arrange
        String query = "Test query";
        doThrow(new BedrockService.BedrockException("Service unavailable"))
                .when(bedrockService).invokeModel(anyString());

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.GOVERNMENT, result);
        verify(bedrockService, times(1)).invokeModel(anyString());
    }

    @Test
    void testClassifyQuery_UnrecognizedResponse_DefaultsToGovernment() throws BedrockService.BedrockException {
        // Arrange
        String query = "Test query";
        when(bedrockService.invokeModel(anyString())).thenReturn("UNKNOWN_CATEGORY");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.GOVERNMENT, result);
    }

    @Test
    void testClassifyQuery_EmptyResponse_DefaultsToGovernment() throws BedrockService.BedrockException {
        // Arrange
        String query = "Test query";
        when(bedrockService.invokeModel(anyString())).thenReturn("");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.GOVERNMENT, result);
    }

    @Test
    void testGetServiceCategories() {
        // Act
        Set<ServiceCategory> categories = classifier.getServiceCategories();

        // Assert
        assertEquals(4, categories.size());
        assertTrue(categories.contains(ServiceCategory.GOVERNMENT));
        assertTrue(categories.contains(ServiceCategory.HEALTH));
        assertTrue(categories.contains(ServiceCategory.EDUCATION));
        assertTrue(categories.contains(ServiceCategory.EMERGENCY));
    }

    @Test
    void testClassifyQuery_GovernmentKeywords() throws BedrockService.BedrockException {
        // Arrange
        String query = "I need to renew my driver's license";
        when(bedrockService.invokeModel(anyString())).thenReturn("GOVERNMENT");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.GOVERNMENT, result);
    }

    @Test
    void testClassifyQuery_HealthKeywords() throws BedrockService.BedrockException {
        // Arrange
        String query = "I need vaccination information";
        when(bedrockService.invokeModel(anyString())).thenReturn("HEALTH");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.HEALTH, result);
    }

    @Test
    void testClassifyQuery_EducationKeywords() throws BedrockService.BedrockException {
        // Arrange
        String query = "Are there any scholarships available?";
        when(bedrockService.invokeModel(anyString())).thenReturn("EDUCATION");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.EDUCATION, result);
    }

    @Test
    void testClassifyQuery_EmergencyKeywords() throws BedrockService.BedrockException {
        // Arrange
        String query = "I need urgent help, there's a fire!";
        when(bedrockService.invokeModel(anyString())).thenReturn("EMERGENCY");

        // Act
        ServiceCategory result = classifier.classifyQuery(query);

        // Assert
        assertEquals(ServiceCategory.EMERGENCY, result);
    }
}

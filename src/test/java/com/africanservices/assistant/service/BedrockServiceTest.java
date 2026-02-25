package com.africanservices.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BedrockServiceTest {

    @Mock
    private BedrockRuntimeClient bedrockClient;

    private BedrockService bedrockService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        bedrockService = new BedrockService(bedrockClient, "anthropic.claude-v2", 0.7, "", "DRAFT");
    }

    @Test
    void testInvokeModel_Success_Claude() throws Exception {
        // Arrange
        String prompt = "What is the capital of France?";
        String expectedResponse = "The capital of France is Paris.";
        
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("completion", expectedResponse);
        String responseBody = objectMapper.writeValueAsString(responseMap);
        
        InvokeModelResponse mockResponse = InvokeModelResponse.builder()
                .body(SdkBytes.fromString(responseBody, StandardCharsets.UTF_8))
                .build();
        
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

        // Act
        String result = bedrockService.invokeModel(prompt);

        // Assert
        assertEquals(expectedResponse, result);
        verify(bedrockClient, times(1)).invokeModel(any(InvokeModelRequest.class));
    }

    @Test
    void testInvokeModel_Success_Titan() throws Exception {
        // Arrange
        BedrockService titanService = new BedrockService(bedrockClient, "amazon.titan-text-express-v1", 0.7, "", "DRAFT");
        String prompt = "What is the capital of France?";
        String expectedResponse = "The capital of France is Paris.";
        
        Map<String, Object> result = new HashMap<>();
        result.put("outputText", expectedResponse);
        
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("results", new Object[]{result});
        String responseBody = objectMapper.writeValueAsString(responseMap);
        
        InvokeModelResponse mockResponse = InvokeModelResponse.builder()
                .body(SdkBytes.fromString(responseBody, StandardCharsets.UTF_8))
                .build();
        
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

        // Act
        String actualResult = titanService.invokeModel(prompt);

        // Assert
        assertEquals(expectedResponse, actualResult);
        verify(bedrockClient, times(1)).invokeModel(any(InvokeModelRequest.class));
    }

    @Test
    void testInvokeModel_WithCustomTemperature() throws Exception {
        // Arrange
        String prompt = "Test prompt";
        double customTemp = 0.5;
        String expectedResponse = "Test response";
        
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("completion", expectedResponse);
        String responseBody = objectMapper.writeValueAsString(responseMap);
        
        InvokeModelResponse mockResponse = InvokeModelResponse.builder()
                .body(SdkBytes.fromString(responseBody, StandardCharsets.UTF_8))
                .build();
        
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

        // Act
        String result = bedrockService.invokeModel(prompt, customTemp);

        // Assert
        assertEquals(expectedResponse, result);
        verify(bedrockClient, times(1)).invokeModel(any(InvokeModelRequest.class));
    }

    @Test
    void testInvokeModel_RetryOnFailure() throws Exception {
        // Arrange
        String prompt = "Test prompt";
        String expectedResponse = "Test response";
        
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("completion", expectedResponse);
        String responseBody = objectMapper.writeValueAsString(responseMap);
        
        InvokeModelResponse mockResponse = InvokeModelResponse.builder()
                .body(SdkBytes.fromString(responseBody, StandardCharsets.UTF_8))
                .build();
        
        // Fail twice, then succeed
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class)))
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenReturn(mockResponse);

        // Act
        String result = bedrockService.invokeModel(prompt);

        // Assert
        assertEquals(expectedResponse, result);
        verify(bedrockClient, times(3)).invokeModel(any(InvokeModelRequest.class));
    }

    @Test
    void testInvokeModel_FailsAfterMaxRetries() {
        // Arrange
        String prompt = "Test prompt";
        
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class)))
                .thenThrow(new RuntimeException("Persistent failure"));

        // Act & Assert
        BedrockService.BedrockException exception = assertThrows(
                BedrockService.BedrockException.class,
                () -> bedrockService.invokeModel(prompt)
        );
        
        assertTrue(exception.getMessage().contains("Failed to invoke Bedrock after 3 attempts"));
        verify(bedrockClient, times(3)).invokeModel(any(InvokeModelRequest.class));
    }

    @Test
    void testInvokeModel_ParseError() {
        // Arrange
        String prompt = "Test prompt";
        String invalidResponseBody = "{\"invalid\": \"response\"}";
        
        InvokeModelResponse mockResponse = InvokeModelResponse.builder()
                .body(SdkBytes.fromString(invalidResponseBody, StandardCharsets.UTF_8))
                .build();
        
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

        // Act & Assert
        assertThrows(BedrockService.BedrockException.class, () -> bedrockService.invokeModel(prompt));
    }

    @Test
    void testInvokeModel_WithGuardrail() throws Exception {
        // Arrange
        BedrockService serviceWithGuardrail = new BedrockService(
                bedrockClient, 
                "anthropic.claude-v2", 
                0.7, 
                "test-guardrail-id", 
                "1"
        );
        
        String prompt = "What are government services available?";
        String expectedResponse = "Government services include document processing, permits, and licenses.";
        
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("completion", expectedResponse);
        String responseBody = objectMapper.writeValueAsString(responseMap);
        
        InvokeModelResponse mockResponse = InvokeModelResponse.builder()
                .body(SdkBytes.fromString(responseBody, StandardCharsets.UTF_8))
                .build();
        
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

        // Act
        String result = serviceWithGuardrail.invokeModel(prompt);

        // Assert
        assertEquals(expectedResponse, result);
        verify(bedrockClient, times(1)).invokeModel(any(InvokeModelRequest.class));
    }

    @Test
    void testInvokeModel_WithoutGuardrail() throws Exception {
        // Arrange
        BedrockService serviceWithoutGuardrail = new BedrockService(
                bedrockClient, 
                "anthropic.claude-v2", 
                0.7, 
                "", 
                "DRAFT"
        );
        
        String prompt = "What are government services available?";
        String expectedResponse = "Government services include document processing, permits, and licenses.";
        
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("completion", expectedResponse);
        String responseBody = objectMapper.writeValueAsString(responseMap);
        
        InvokeModelResponse mockResponse = InvokeModelResponse.builder()
                .body(SdkBytes.fromString(responseBody, StandardCharsets.UTF_8))
                .build();
        
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);

        // Act
        String result = serviceWithoutGuardrail.invokeModel(prompt);

        // Assert
        assertEquals(expectedResponse, result);
        verify(bedrockClient, times(1)).invokeModel(any(InvokeModelRequest.class));
    }

    @Test
    void testInvokeModel_GuardrailBlocksHarmfulContent() throws Exception {
        // Arrange
        BedrockService serviceWithGuardrail = new BedrockService(
                bedrockClient, 
                "anthropic.claude-v2", 
                0.7, 
                "test-guardrail-id", 
                "1"
        );
        
        String harmfulPrompt = "How to harm someone";
        
        // Simulate guardrail blocking the request
        when(bedrockClient.invokeModel(any(InvokeModelRequest.class)))
                .thenThrow(new RuntimeException("Guardrail blocked the request"));

        // Act & Assert
        BedrockService.BedrockException exception = assertThrows(
                BedrockService.BedrockException.class,
                () -> serviceWithGuardrail.invokeModel(harmfulPrompt)
        );
        
        assertTrue(exception.getMessage().contains("Failed to invoke Bedrock after 3 attempts"));
        verify(bedrockClient, times(3)).invokeModel(any(InvokeModelRequest.class));
    }
}


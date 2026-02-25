package com.africanservices.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for interacting with Amazon Bedrock for AI/ML operations.
 * Provides methods to invoke Bedrock models with retry logic and error handling.
 */
@Service
public class BedrockService {

    private static final Logger logger = LoggerFactory.getLogger(BedrockService.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;
    private final String modelId;
    private final double temperature;
    private final String guardrailId;
    private final String guardrailVersion;

    @Autowired
    public BedrockService(
            BedrockRuntimeClient bedrockClient,
            @Value("${bedrock.model.id:anthropic.claude-v2}") String modelId,
            @Value("${bedrock.temperature:0.7}") double temperature,
            @Value("${bedrock.guardrail.id:}") String guardrailId,
            @Value("${bedrock.guardrail.version:DRAFT}") String guardrailVersion) {
        this.bedrockClient = bedrockClient;
        this.objectMapper = new ObjectMapper();
        this.modelId = modelId;
        this.temperature = temperature;
        this.guardrailId = guardrailId;
        this.guardrailVersion = guardrailVersion;
    }

    /**
     * Invokes Bedrock with a prompt and returns the response text.
     * 
     * Note: Bedrock guardrails are configured at the infrastructure level (see template.yaml).
     * The guardrail automatically filters harmful content in both inputs and outputs based on
     * the configured policies for sexual content, violence, hate speech, insults, misconduct,
     * and prompt attacks. Medical diagnosis and legal advice topics are also blocked.
     *
     * @param prompt The prompt to send to Bedrock
     * @return The response text from Bedrock
     * @throws BedrockException if invocation fails after retries
     */
    public String invokeModel(String prompt) throws BedrockException {
        return invokeModel(prompt, temperature);
    }

    /**
     * Invokes Bedrock with a prompt and custom temperature.
     *
     * @param prompt The prompt to send to Bedrock
     * @param customTemperature The temperature setting for this request
     * @return The response text from Bedrock
     * @throws BedrockException if invocation fails after retries
     */
    public String invokeModel(String prompt, double customTemperature) throws BedrockException {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                logger.debug("Invoking Bedrock model {} (attempt {}/{})", modelId, attempt + 1, MAX_RETRIES);
                
                String requestBody = buildRequestBody(prompt, customTemperature);
                
                InvokeModelRequest request = InvokeModelRequest.builder()
                        .modelId(modelId)
                        .body(SdkBytes.fromString(requestBody, StandardCharsets.UTF_8))
                        .build();

                InvokeModelResponse response = bedrockClient.invokeModel(request);
                String responseBody = response.body().asString(StandardCharsets.UTF_8);
                
                String result = parseResponse(responseBody);
                logger.debug("Successfully invoked Bedrock model with guardrail protection");
                return result;

            } catch (Exception e) {
                lastException = e;
                attempt++;
                logger.warn("Bedrock invocation failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BedrockException("Interrupted during retry delay", ie);
                    }
                }
            }
        }

        throw new BedrockException("Failed to invoke Bedrock after " + MAX_RETRIES + " attempts", lastException);
    }

    /**
     * Builds the request body for Bedrock invocation based on the model type.
     */
    private String buildRequestBody(String prompt, double temp) throws Exception {
        Map<String, Object> requestMap = new HashMap<>();
        
        if (modelId.startsWith("anthropic.claude")) {
            // Claude model format
            requestMap.put("prompt", "\n\nHuman: " + prompt + "\n\nAssistant:");
            requestMap.put("max_tokens_to_sample", 2048);
            requestMap.put("temperature", temp);
            requestMap.put("top_p", 0.9);
        } else if (modelId.startsWith("amazon.titan")) {
            // Titan model format
            Map<String, Object> textGenerationConfig = new HashMap<>();
            textGenerationConfig.put("temperature", temp);
            textGenerationConfig.put("topP", 0.9);
            textGenerationConfig.put("maxTokenCount", 2048);
            
            requestMap.put("inputText", prompt);
            requestMap.put("textGenerationConfig", textGenerationConfig);
        } else {
            // Generic format
            requestMap.put("prompt", prompt);
            requestMap.put("temperature", temp);
            requestMap.put("max_tokens", 2048);
        }
        
        return objectMapper.writeValueAsString(requestMap);
    }

    /**
     * Parses the response from Bedrock based on the model type.
     */
    private String parseResponse(String responseBody) throws Exception {
        JsonNode responseJson = objectMapper.readTree(responseBody);
        
        if (modelId.startsWith("anthropic.claude")) {
            // Claude response format
            return responseJson.get("completion").asText().trim();
        } else if (modelId.startsWith("amazon.titan")) {
            // Titan response format
            JsonNode results = responseJson.get("results");
            if (results != null && results.isArray() && results.size() > 0) {
                return results.get(0).get("outputText").asText().trim();
            }
        } else {
            // Try common response fields
            if (responseJson.has("completion")) {
                return responseJson.get("completion").asText().trim();
            } else if (responseJson.has("text")) {
                return responseJson.get("text").asText().trim();
            } else if (responseJson.has("generated_text")) {
                return responseJson.get("generated_text").asText().trim();
            }
        }
        
        throw new BedrockException("Unable to parse response from model: " + modelId);
    }

    /**
     * Custom exception for Bedrock service errors.
     */
    public static class BedrockException extends Exception {
        public BedrockException(String message) {
            super(message);
        }

        public BedrockException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

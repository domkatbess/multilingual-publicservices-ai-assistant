package com.africanservices.assistant.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model for successful query processing.
 * Contains the generated response text, detected language, intent classification,
 * and optional audio URL for text-to-speech output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuccessResponse(
    @JsonProperty("responseText")
    String responseText,

    @JsonProperty("detectedLanguage")
    String detectedLanguage,

    @JsonProperty("intent")
    String intent,

    @JsonProperty("audioUrl")
    String audioUrl,

    @JsonProperty("audioExpiration")
    Long audioExpiration
) {
    /**
     * Constructor for responses without audio.
     */
    public SuccessResponse(String responseText, String detectedLanguage, String intent) {
        this(responseText, detectedLanguage, intent, null, null);
    }

    /**
     * Constructor for responses with audio.
     */
    public SuccessResponse(String responseText, String detectedLanguage, String intent, 
                          String audioUrl, Long audioExpiration) {
        this.responseText = responseText;
        this.detectedLanguage = detectedLanguage;
        this.intent = intent;
        this.audioUrl = audioUrl;
        this.audioExpiration = audioExpiration;
    }
}

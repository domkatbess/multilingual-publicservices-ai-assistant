package com.africanservices.assistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model for error conditions.
 * Contains error details including error code, localized message,
 * fallback message in English, and retry indication.
 */
public record ErrorResponse(
    @JsonProperty("error")
    boolean error,

    @JsonProperty("errorCode")
    String errorCode,

    @JsonProperty("message")
    String message,

    @JsonProperty("fallbackMessage")
    String fallbackMessage,

    @JsonProperty("retryable")
    boolean retryable
) {
    /**
     * Constructor that defaults error flag to true.
     */
    public ErrorResponse(String errorCode, String message, String fallbackMessage, boolean retryable) {
        this(true, errorCode, message, fallbackMessage, retryable);
    }
}

package com.africanservices.assistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request model for text-based user queries.
 * Represents input from users submitting text messages to the assistant.
 */
public record InputRequest(
    @JsonProperty("userId")
    @NotBlank(message = "User ID is required")
    String userId,

    @JsonProperty("sessionId")
    @NotBlank(message = "Session ID is required")
    String sessionId,

    @JsonProperty("message")
    @NotBlank(message = "Message cannot be empty")
    @Size(max = 999, message = "Message must be less than 1000 characters")
    String message,

    @JsonProperty("preferredLanguage")
    String preferredLanguage
) {
}

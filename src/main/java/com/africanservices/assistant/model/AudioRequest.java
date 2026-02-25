package com.africanservices.assistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request model for voice-based user queries.
 * Represents audio input from users submitting voice messages to the assistant.
 */
public record AudioRequest(
    @JsonProperty("userId")
    @NotBlank(message = "User ID is required")
    String userId,

    @JsonProperty("sessionId")
    @NotBlank(message = "Session ID is required")
    String sessionId,

    @JsonProperty("audioData")
    @NotBlank(message = "Audio data is required")
    String audioData,

    @JsonProperty("audioFormat")
    @NotBlank(message = "Audio format is required")
    @Pattern(regexp = "(?i)(wav|mp3|m4a)", message = "Audio format must be WAV, MP3, or M4A")
    String audioFormat
) {
}

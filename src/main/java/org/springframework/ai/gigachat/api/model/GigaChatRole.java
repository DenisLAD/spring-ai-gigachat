package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enum representing the possible roles in a GigaChat conversation.
 */
@AllArgsConstructor
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum GigaChatRole {
    /**
     * Represents a system role in the conversation.
     */
    SYSTEM("system"),

    /**
     * Represents an assistant role in the conversation.
     */
    ASSISTANT("assistant"),

    /**
     * Represents a user role in the conversation.
     */
    USER("user"),

    /**
     * Represents a function role in the conversation.
     */
    FUNCTION("function");

    /**
     * The string value associated with this GigaChat role.
     */
    @JsonValue
    private final String value;
}
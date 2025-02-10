package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum GigaChatRole {
    SYSTEM("system"),
    ASSISTANT("assistant"),
    USER("user"),
    FUNCTION("function");

    @JsonValue
    private final String value;
}
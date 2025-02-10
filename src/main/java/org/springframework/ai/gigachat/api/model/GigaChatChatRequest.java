package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class GigaChatChatRequest {

    private @JsonProperty("model") String model;
    private @JsonProperty("messages") Collection<Message> messages;
    private @JsonProperty("functions") Collection<Function> functions;
    private @JsonProperty("temperature") Float temperature;
    private @JsonProperty("top_p") Float topP;
    private @JsonProperty("stream") Boolean stream;
    private @JsonProperty("max_tokens") Integer maxTokens;
    private @JsonProperty("repetition_penalty") Float repetitionPenalty;
    private @JsonProperty("update_interval") Integer updateInterval;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class Message {
        private @JsonProperty("role") GigaChatRole role;
        private @JsonProperty("content") Object content;
        private @JsonProperty("function_state_id") UUID functionStateId;
        private @JsonProperty("attachments") Collection<String> attachments;
        private @JsonProperty("function_call") Object functionCall;
        private @JsonProperty("name") String name;
    }

    @AllArgsConstructor
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        private @JsonProperty("name") String name;
        private @JsonProperty("description") String description;
        private @JsonProperty("parameters") Map<String, Object> parameters;
        private @JsonProperty("few_shot_examples") Collection<FunctionExample> fewShotExamples;
        private @JsonProperty("return_parameters") Map<String, Object> returnParameters;
    }

    @AllArgsConstructor
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionExample {
        private @JsonProperty("request") String request;
        private @JsonProperty("params") Map<String, Object> params;
    }

    @AllArgsConstructor
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionCallRequest {
        private @JsonProperty("name") String name;
        private @JsonProperty("partial_arguments") Map<String, Object> partialArguments;
    }

}

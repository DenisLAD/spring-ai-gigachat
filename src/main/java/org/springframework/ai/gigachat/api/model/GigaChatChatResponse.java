package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class GigaChatChatResponse {

    private @JsonProperty("choices") Collection<Choice> choices;
    private @JsonProperty("created") Long created;
    private @JsonProperty("model") String model;
    private @JsonProperty("usage") Usage usage;
    private @JsonProperty("object") String object;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class Choice {
        private @JsonProperty("message") Message message;
        private @JsonProperty("index") Integer index;
        private @JsonProperty("finish_reason") String finishReason;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class Message {
        private @JsonProperty("role") GigaChatRole role;
        private @JsonProperty("content") String content;
        private @JsonProperty("created") Long created;
        private @JsonProperty("name") String name;
        private @JsonProperty("function_state_id") UUID functionStateId;
        private @JsonProperty("function_call") FunctionCall functionCall;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class FunctionCall {
        private @JsonProperty("name") String name;
        private @JsonProperty("arguments") Map<String, Object> arguments;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class Usage {
        private @JsonProperty("prompt_tokens") Integer promptTokens;
        private @JsonProperty("completion_tokens") Integer completionTokens;
        private @JsonProperty("total_tokens") Integer totalTokens;
    }
}

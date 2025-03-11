package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Represents the response from a GigaChat chat.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class GigaChatChatResponse {

    /**
     * A collection of choices returned by the chat system.
     */
    private @JsonProperty("choices") Collection<Choice> choices;

    /**
     * The timestamp when the response was created.
     */
    private @JsonProperty("created") Long created;

    /**
     * The name of the model used to generate this response.
     */
    private @JsonProperty("model") String model;

    /**
     * Usage statistics related to the chat completion, such as token counts.
     */
    private @JsonProperty("usage") Usage usage;

    /**
     * A string indicating the type of object returned in the response.
     */
    private @JsonProperty("object") String object;

    /**
     * Represents a single choice within the GigaChat chat response.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class Choice {
        /**
         * The full message corresponding to this choice.
         */
        private @JsonProperty("message") Message message;

        /**
         * The delta message, which represents incremental updates in the conversation.
         */
        private @JsonProperty("delta") Message delta;

        /**
         * The index of this choice within the collection of choices.
         */
        private @JsonProperty("index") Integer index;

        /**
         * The reason why this choice was marked as finished.
         */
        private @JsonProperty("finish_reason") String finishReason;
    }

    /**
     * Represents a message in the GigaChat conversation.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class Message {
        /**
         * The role of the sender of this message (e.g., user, assistant).
         */
        private @JsonProperty("role") GigaChatRole role;

        /**
         * The content of the message.
         */
        private @JsonProperty("content") String content;

        /**
         * The timestamp when the message was created.
         */
        private @JsonProperty("created") Long created;

        /**
         * The name of the function being called.
         */
        private @JsonProperty("name") String name;

        /**
         * A unique identifier for the function state, if any.
         */
        private @JsonProperty("function_state_id") UUID functionStateId;

        /**
         * Information about a function call associated with this message.
         */
        private @JsonProperty("function_call") FunctionCall functionCall;
    }

    /**
     * Represents a function call within a GigaChat message.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class FunctionCall {
        /**
         * The name of the function being called.
         */
        private @JsonProperty("name") String name;

        /**
         * Arguments passed to the function, represented as key-value pairs.
         */
        private @JsonProperty("arguments") Map<String, Object> arguments;
    }

    /**
     * Represents usage statistics for a GigaChat chat completion.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class Usage {
        /**
         * The number of tokens used in the prompt.
         */
        private @JsonProperty("prompt_tokens") Integer promptTokens;

        /**
         * The number of tokens generated in the completion.
         */
        private @JsonProperty("completion_tokens") Integer completionTokens;

        /**
         * The total number of tokens used (sum of prompt and completion tokens).
         */
        private @JsonProperty("total_tokens") Integer totalTokens;
    }
}

package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a chat request for the GigaChat API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class GigaChatChatRequest {
    /**
     * The model to be used for the chat.
     */
    private @JsonProperty("model") String model;

    /**
     * The collection of messages in the chat.
     */
    private @JsonProperty("messages") Collection<Message> messages;

    /**
     * The function call specification.
     */
    private @JsonProperty("function_call") String functionCall;

    /**
     * The collection of functions available for the chat.
     */
    private @JsonProperty("functions") Collection<Function> functions;

    /**
     * The temperature setting for generating responses.
     */
    private @JsonProperty("temperature") Double temperature;

    /**
     * The top-p (nucleus) sampling parameter for generation.
     */
    private @JsonProperty("top_p") Double topP;

    /**
     * Indicates whether the response should be streamed.
     */
    private @JsonProperty("stream") Boolean stream;

    /**
     * The maximum number of tokens in the response.
     */
    private @JsonProperty("max_tokens") Integer maxTokens;

    /**
     * The penalty for repeated tokens in the response.
     */
    private @JsonProperty("repetition_penalty") Double repetitionPenalty;

    /**
     * The interval for updating the chat state.
     */
    private @JsonProperty("update_interval") Integer updateInterval;

    /**
     * Represents a message within a GigaChat conversation.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class Message {
        /**
         * The role of the message sender.
         */
        private @JsonProperty("role") GigaChatRole role;

        /**
         * The content of the message.
         */
        private @JsonProperty("content") Object content;

        /**
         * The unique identifier for the function state.
         */
        private @JsonProperty("function_state_id") UUID functionStateId;

        /**
         * The function call associated with the message.
         */
        private @JsonProperty("function_call") Object functionCall;

        /**
         * The collection of attachments in the message.
         */
        private @JsonProperty("attachments") Collection<String> attachments;

        /**
         * The name of the function being called.
         */
        private @JsonProperty("name") String name;
    }

    /**
     * Represents a function available for use in a GigaChat conversation.
     */
    @AllArgsConstructor
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        /**
         * The name of the function.
         */
        private @JsonProperty("name") String name;

        /**
         * The description of the function.
         */
        private @JsonProperty("description") String description;

        /**
         * The parameters required for the function.
         */
        private @JsonProperty("parameters") Map<String, Object> parameters;

        /**
         * The collection of few-shot examples for the function.
         */
        private @JsonProperty("few_shot_examples") Collection<FunctionExample> fewShotExamples;

        /**
         * The return parameters expected from the function.
         */
        private @JsonProperty("return_parameters") Map<String, Object> returnParameters;
    }

    /**
     * Represents a function example used in a GigaChat conversation.
     */
    @AllArgsConstructor
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionExample {
        /**
         * The request for the function example.
         */
        private @JsonProperty("request") String request;

        /**
         * The parameters for the function example.
         */
        private @JsonProperty("params") Map<String, Object> params;
    }

    /**
     * Represents a request to call a function in a GigaChat conversation.
     */
    @AllArgsConstructor
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionCallRequest {
        /**
         * The name of the function to be called.
         */
        private @JsonProperty("name") String name;

        /**
         * The partial arguments for the function call.
         */
        private @JsonProperty("partial_arguments") Map<String, Object> partialArguments;

        /**
         * The complete arguments for the function call.
         */
        private @JsonProperty("arguments") Map<String, Object> arguments;
    }
}

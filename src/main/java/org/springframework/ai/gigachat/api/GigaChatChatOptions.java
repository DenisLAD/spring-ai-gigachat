package org.springframework.ai.gigachat.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class GigaChatChatOptions implements ToolCallingChatOptions, EmbeddingOptions {

    /**
     * The name of the model to be used for generating responses in the chat session.
     */
    private @JsonProperty("model") String model;

    /**
     * Controls the randomness of predictions by scaling the logits before applying softmax.
     * Higher values make the output more random, while lower values make it more deterministic.
     */
    private @JsonProperty("temperature") Double temperature;

    /**
     * An alternative to sampling with temperature. This parameter nucleus sampling considers
     * the smallest possible set of tokens whose cumulative probability exceeds the top_p value.
     */
    private @JsonProperty("top_p") Double topP;

    /**
     * Whether the API should stream its responses incrementally rather than sending them as a single response.
     */
    private @JsonProperty("stream") Boolean stream;

    /**
     * The maximum number of tokens to generate in the completion. If not set, the model will use its default value.
     */
    private @JsonProperty("max_tokens") Integer maxTokens;

    /**
     * The penalty applied to the log probability of repeating the same token.
     * A higher value decreases the likelihood of repetition.
     */
    private @JsonProperty("repetition_penalty") Double frequencyPenalty;

    /**
     * The interval at which the model should update its internal state, if applicable.
     */
    @lombok.Builder.Default
    private @JsonProperty("update_interval") Integer updateInterval = 0;

    /**
     * Metadata associated with the chat session. This can be used to store additional context or information.
     */
    private @JsonProperty("metadata") Map<String, String> metadata;

    /**
     * Whether the generated responses should be stored for future reference.
     */
    private @JsonProperty("store") Boolean store;

    /**
     * List of function callbacks that can be triggered during the chat session.
     */
    @lombok.Builder.Default
    private @JsonIgnore List<FunctionCallback> toolCallbacks = new ArrayList<>();

    /**
     * Set of names representing tools that can be called during the chat session.
     */
    @lombok.Builder.Default
    private @JsonIgnore Set<String> toolNames = new HashSet<>();

    /**
     * Contextual data for the tools, which may influence their behavior.
     */
    private @JsonIgnore Map<String, Object> toolContext;

    /**
     * Indicates whether internal tool execution is enabled.
     */
    private @JsonIgnore Boolean internalToolExecutionEnabled;

    @Override
    public List<FunctionCallback> getFunctionCallbacks() {
        return getToolCallbacks();
    }

    @Override
    public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
        setToolCallbacks(functionCallbacks);
    }

    @Override
    public Set<String> getFunctions() {
        return getToolNames();
    }

    @Override
    public void setFunctions(Set<String> functions) {
        setToolNames(functions);
    }

    /**
     * Determines whether tool calls should be proxied.
     *
     * @return A boolean indicating whether internal tool execution is enabled.
     */
    @JsonIgnore
    public Boolean getProxyToolCalls() {
        return this.internalToolExecutionEnabled != null ? !this.internalToolExecutionEnabled : null;
    }

    /**
     * Sets the proxy tool calls configuration.
     *
     * @param proxyToolCalls A boolean indicating whether to enable or disable internal tool execution.
     */
    @JsonIgnore
    public void setProxyToolCalls(Boolean proxyToolCalls) {
        this.internalToolExecutionEnabled = proxyToolCalls != null ? !proxyToolCalls : null;
    }

    @Override
    public Integer getTopK() {
        return null;
    }

    /**
     * Creates a copy of the current chat options.
     *
     * @return A new instance of GigaChatChatOptions with the same configuration as this one.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ChatOptions> T copy() {
        return (T) this.toBuilder().build();
    }

    @Override
    public Double getPresencePenalty() {
        return null;
    }

    @Override
    public List<String> getStopSequences() {
        return null;
    }

    @Override
    public Integer getDimensions() {
        return null;
    }

    /**
     * Checks if internal tool execution is enabled.
     *
     * @return A boolean indicating whether internal tool execution is enabled.
     */
    @Override
    public Boolean isInternalToolExecutionEnabled() {
        return internalToolExecutionEnabled;
    }
}

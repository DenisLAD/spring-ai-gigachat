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

    private @JsonProperty("model") String model;
    private @JsonProperty("temperature") Double temperature;
    private @JsonProperty("top_p") Double topP;
    private @JsonProperty("stream") Boolean stream;
    private @JsonProperty("max_tokens") Integer maxTokens;
    private @JsonProperty("repetition_penalty") Double frequencyPenalty;
    private @JsonProperty("update_interval") Integer updateInterval = 0;
    private @JsonProperty("metadata") Map<String, String> metadata;
    private @JsonProperty("store") Boolean store;

    @JsonIgnore
    private List<FunctionCallback> toolCallbacks = new ArrayList<>();

    @JsonIgnore
    private Set<String> toolNames = new HashSet<>();

    @JsonIgnore
    private Map<String, Object> toolContext;

    @JsonIgnore
    private Boolean internalToolExecutionEnabled;

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

    @JsonIgnore
    public Boolean getProxyToolCalls() {
        return this.internalToolExecutionEnabled != null ? !this.internalToolExecutionEnabled : null;
    }

    @JsonIgnore
    public void setProxyToolCalls(Boolean proxyToolCalls) {
        this.internalToolExecutionEnabled = proxyToolCalls != null ? !proxyToolCalls : null;
    }

    @Override
    public Integer getTopK() {
        return null;
    }

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

    @Override
    public Boolean isInternalToolExecutionEnabled() {
        return internalToolExecutionEnabled;
    }
}

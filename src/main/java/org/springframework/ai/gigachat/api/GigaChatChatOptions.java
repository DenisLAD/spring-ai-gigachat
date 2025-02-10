package org.springframework.ai.gigachat.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class GigaChatChatOptions implements FunctionCallingOptions, EmbeddingOptions {

    @Setter
    private @JsonProperty("model") String model;
    @Setter
    private @JsonProperty("temperature") Double temperature;
    @Setter
    private @JsonProperty("top_p") Double topP;
    @Getter
    @Setter
    private @JsonProperty("stream") Boolean stream;
    @Setter
    private @JsonProperty("max_tokens") Integer maxTokens;
    @Setter
    private @JsonProperty("repetition_penalty") Double frequencyPenalty;
    @Setter
    @Getter
    private @JsonProperty("update_interval") Integer updateInterval = 0;
    @Setter
    @Getter
    private @JsonProperty("metadata") Map<String, String> metadata;
    @Setter
    @Getter
    private @JsonProperty("store") Boolean store;

    @JsonIgnore
    private List<FunctionCallback> functionCallbacks = new ArrayList<>();

    @JsonIgnore
    private Set<String> functions = new HashSet<>();

    @JsonIgnore
    private Boolean proxyToolCalls;

    @JsonIgnore
    private Map<String, Object> toolContext;

    public Float getTemperatureF() {
        return Optional.ofNullable(temperature).map(Number::floatValue).orElse(null);
    }

    @Override
    public Integer getTopK() {
        return null;
    }

    public Float getTopPF() {
        return Optional.ofNullable(topP).map(Number::floatValue).orElse(null);
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

    public Float getFrequencyPenaltyF() {
        return Optional.ofNullable(frequencyPenalty).map(Number::floatValue).orElse(null);
    }

    @Override
    public Integer getDimensions() {
        return null;
    }
}

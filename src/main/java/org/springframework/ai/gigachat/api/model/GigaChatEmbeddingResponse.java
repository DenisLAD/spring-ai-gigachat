package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Collection;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class GigaChatEmbeddingResponse {
    private @JsonProperty("object") String object;
    private @JsonProperty("data") Collection<EmbeddingData> data;
    private @JsonProperty("model") String model;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class EmbeddingData {
        private @JsonProperty("object") String object;
        private @JsonProperty("embedding") float[] embedding;
        private @JsonProperty("index") Integer index;
        private @JsonProperty("usage") Usage usage;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class Usage {
        private @JsonProperty("prompt_tokens") Integer promptTokens;
    }
}

package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Collection;

/**
 * Represents the response from the GigaChat embedding API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class GigaChatEmbeddingResponse {
    /**
     * The type of object returned in the response.
     */
    private @JsonProperty("object") String object;

    /**
     * A collection of {@link EmbeddingData} objects containing embedding information.
     */
    private @JsonProperty("data") Collection<EmbeddingData> data;

    /**
     * The name of the model used to generate the embeddings.
     */
    private @JsonProperty("model") String model;

    /**
     * Represents a single piece of embedding data returned by the API.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class EmbeddingData {
        /**
         * The type of object this data represents.
         */
        private @JsonProperty("object") String object;

        /**
         * An array of floating-point values representing the embedding.
         */
        private @JsonProperty("embedding") float[] embedding;

        /**
         * The index associated with this embedding data, if applicable.
         */
        private @JsonProperty("index") Integer index;

        /**
         * Usage information for this embedding data.
         */
        private @JsonProperty("usage") Usage usage;
    }

    /**
     * Represents the usage statistics for the API request.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class Usage {
        /**
         * The number of tokens used in the prompt.
         */
        private @JsonProperty("prompt_tokens") Integer promptTokens;
    }
}

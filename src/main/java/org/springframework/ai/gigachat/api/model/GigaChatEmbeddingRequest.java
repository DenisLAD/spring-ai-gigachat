package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

/**
 * Represents a request for generating embeddings using the GigaChat API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GigaChatEmbeddingRequest {
    /**
     * The name of the model to be used for generating the embedding.
     */
    private @JsonProperty("model") String model;

    /**
     * A collection of input strings for which embeddings are to be generated.
     */
    private @JsonProperty("input") Collection<String> input;
}

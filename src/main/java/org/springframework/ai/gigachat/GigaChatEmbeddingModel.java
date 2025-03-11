package org.springframework.ai.gigachat;

import io.micrometer.observation.ObservationRegistry;
import lombok.Setter;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.gigachat.api.GigaChatApi;
import org.springframework.ai.gigachat.api.GigaChatChatOptions;
import org.springframework.ai.gigachat.api.model.GigaChatEmbeddingRequest;
import org.springframework.ai.gigachat.api.model.GigaChatEmbeddingResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Represents a Giga Chat embedding model, which extends the abstract class AbstractEmbeddingModel.
 */
public class GigaChatEmbeddingModel extends AbstractEmbeddingModel {

    /**
     * Default observation convention for embedding models.
     */
    private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

    /**
     * The Giga Chat API to interact with the embedding service.
     */
    private final GigaChatApi chatApi;

    /**
     * Default options for the Giga Chat embedding model.
     */
    private final GigaChatChatOptions defaultOptions;

    /**
     * Observation registry used for observing and logging the model's operations.
     */
    private final ObservationRegistry observationRegistry;

    /**
     * Observation convention to be used for this model.
     */
    @Setter
    private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    /**
     * Constructs a new GigaChatEmbeddingModel instance with the provided parameters.
     *
     * @param chatApi             The Giga Chat API instance to interact with the embedding service.
     * @param defaultOptions      Default options for the embedding model.
     * @param observationRegistry Observation registry used for observing and logging.
     */
    public GigaChatEmbeddingModel(GigaChatApi chatApi, GigaChatChatOptions defaultOptions, ObservationRegistry observationRegistry) {
        this.chatApi = chatApi;
        this.defaultOptions = defaultOptions;
        this.observationRegistry = observationRegistry;
    }

    /**
     * Calls the embedding API with the provided request and returns the response.
     *
     * @param request The embedding request containing instructions and options.
     * @return The embedding response from the Giga Chat API.
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Assert.notEmpty(request.getInstructions(), "Нужен текст!");
        GigaChatEmbeddingRequest embeddingRequest = embeddingRequest(request.getInstructions(), request.getOptions());
        EmbeddingModelObservationContext observationContext = EmbeddingModelObservationContext.builder().embeddingRequest(request).provider(GigaChatApi.PROVIDER_NAME).requestOptions(buildRequestOptions(embeddingRequest)).build();
        return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry).observe(() -> {
            GigaChatEmbeddingResponse response = chatApi.embed(embeddingRequest);
            List<Embedding> embeddings = response.getData().stream().map((e) -> new Embedding(e.getEmbedding(), e.getIndex())).toList();
            EmbeddingResponseMetadata embeddingResponseMetadata = new EmbeddingResponseMetadata(response.getModel(), from(response));
            EmbeddingResponse embeddingResponse = new EmbeddingResponse(embeddings, embeddingResponseMetadata);
            observationContext.setResponse(embeddingResponse);
            return embeddingResponse;
        });
    }

    /**
     * Builds the request options based on the provided Giga Chat embedding request.
     *
     * @param request The Giga Chat embedding request.
     * @return The constructed embedding options.
     */
    private EmbeddingOptions buildRequestOptions(GigaChatEmbeddingRequest request) {
        return EmbeddingOptionsBuilder.builder().withModel(request.getModel()).build();
    }

    /**
     * Converts the Giga Chat embedding response to a DefaultUsage object.
     *
     * @param response The Giga Chat embedding response.
     * @return A Usage object representing the prompt tokens used.
     */
    private Usage from(GigaChatEmbeddingResponse response) {
        return new DefaultUsage(response.getData().stream().mapToInt(item -> item.getUsage().getPromptTokens()).sum(), 0);
    }

    /**
     * Creates a Giga Chat embedding request based on input content and options.
     *
     * @param inputContent The list of input texts for embedding.
     * @param options      The embedding options.
     * @return A new GigaChatEmbeddingRequest instance.
     */
    private GigaChatEmbeddingRequest embeddingRequest(List<String> inputContent, EmbeddingOptions options) {
        GigaChatChatOptions runtimeOptions = null;
        if (options instanceof GigaChatChatOptions mergedOptions) {
            runtimeOptions = mergedOptions;
        }

        GigaChatChatOptions mergedOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions, GigaChatChatOptions.class);
        if (!StringUtils.hasText(mergedOptions.getModel())) {
            throw new IllegalArgumentException("Модель не установлена!");
        } else {
            String model = mergedOptions.getModel();
            return new GigaChatEmbeddingRequest(model, inputContent);
        }
    }

    /**
     * Embeds a document by extracting its text and calling the embedding method.
     *
     * @param document The document to be embedded.
     * @return A float array representing the embedding of the document's text.
     */
    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }
}

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

public class GigaChatEmbeddingModel extends AbstractEmbeddingModel {

    private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();
    private final GigaChatApi chatApi;
    private final GigaChatChatOptions defaultOptions;
    private final ObservationRegistry observationRegistry;
    @Setter
    private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    public GigaChatEmbeddingModel(GigaChatApi chatApi, GigaChatChatOptions defaultOptions, ObservationRegistry observationRegistry) {
        this.chatApi = chatApi;
        this.defaultOptions = defaultOptions;
        this.observationRegistry = observationRegistry;
    }

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

    private EmbeddingOptions buildRequestOptions(GigaChatEmbeddingRequest request) {
        return EmbeddingOptionsBuilder.builder().withModel(request.getModel()).build();
    }

    private Usage from(GigaChatEmbeddingResponse response) {
        return new DefaultUsage(response.getData().stream().mapToInt(item -> item.getUsage().getPromptTokens()).sum(), 0);
    }

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

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }
}

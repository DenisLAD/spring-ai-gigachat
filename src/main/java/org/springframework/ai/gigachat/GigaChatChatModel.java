package org.springframework.ai.gigachat;

import io.micrometer.observation.ObservationRegistry;
import lombok.Setter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.gigachat.api.GigaChatApi;
import org.springframework.ai.gigachat.api.GigaChatChatOptions;
import org.springframework.ai.gigachat.api.model.GigaChatChatRequest;
import org.springframework.ai.gigachat.api.model.GigaChatChatResponse;
import org.springframework.ai.gigachat.api.model.GigaChatRole;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GigaChatChatModel extends AbstractToolCallSupport implements ChatModel {

    private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();
    private final GigaChatApi chatApi;
    private final GigaChatChatOptions defaultOptions;
    private final ObservationRegistry observationRegistry;

    @Setter
    private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    public GigaChatChatModel(GigaChatApi chatApi, GigaChatChatOptions defaultOptions, FunctionCallbackResolver functionCallbackContext, List<FunctionCallback> toolFunctionCallbacks, ObservationRegistry observationRegistry) {
        super(functionCallbackContext, defaultOptions, toolFunctionCallbacks);
        this.chatApi = chatApi;
        this.defaultOptions = defaultOptions;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        GigaChatChatRequest request = buildPrompt(prompt, false);
        ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider(GigaChatApi.PROVIDER_NAME)
                .requestOptions(buildRequestOptions(request))
                .build();

        ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                .observation(this.observationConvention,
                        DEFAULT_OBSERVATION_CONVENTION,
                        () -> observationContext,
                        this.observationRegistry)
                .observe(() -> {
                    GigaChatChatResponse gigaChatResponse = chatApi.chat(request);

                    List<AssistantMessage.ToolCall> toolCalls = gigaChatResponse.getChoices().stream()
                            .map(GigaChatChatResponse.Choice::getMessage)
                            .filter(Objects::nonNull)
                            .filter(item -> Objects.nonNull(item.getFunctionCall()))
                            .map(msg -> new AssistantMessage.ToolCall(Optional.ofNullable(msg.getFunctionStateId()).map(Objects::toString).orElse(null), "function", msg.getFunctionCall().getName(), ModelOptionsUtils.toJsonString(msg.getFunctionCall().getArguments())))
                            .toList();

                    AssistantMessage assistantMessage = new AssistantMessage(gigaChatResponse.getChoices().stream()
                            .map(GigaChatChatResponse.Choice::getMessage)
                            .filter(Objects::nonNull)
                            .map(GigaChatChatResponse.Message::getContent)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining())
                            , Map.of(), toolCalls);

                    ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
                    if (gigaChatResponse.getUsage().getPromptTokens() != null && gigaChatResponse.getUsage().getCompletionTokens() != null) {
                        generationMetadata = ChatGenerationMetadata.builder().finishReason(gigaChatResponse.getChoices().stream().reduce((first, last) -> last).orElseThrow().getFinishReason()).build();
                    }

                    var generator = new Generation(assistantMessage, generationMetadata);
                    ChatResponse chatResponse = new ChatResponse(List.of(generator), buildAnswer(gigaChatResponse));

                    observationContext.setResponse(chatResponse);

                    return chatResponse;
                });

        if (!isProxyToolCalls(prompt, this.defaultOptions) && response != null && isToolCall(response, Set.of("function_call"))) {
            var toolCallConversation = handleToolCalls(prompt, response);
            return internalCall(new Prompt(toolCallConversation, prompt.getOptions()), response);
        }

        return response;
    }

    private ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {

        GigaChatChatRequest request = buildPrompt(prompt, false);

        ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider(GigaChatApi.PROVIDER_NAME)
                .requestOptions(buildRequestOptions(request))
                .build();

        ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                .observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
                        this.observationRegistry)
                .observe(() -> {

                   GigaChatChatResponse gigaChatResponse = this.chatApi.chat(request);

                    List<AssistantMessage.ToolCall> toolCalls = gigaChatResponse.getChoices().stream()
                            .map(GigaChatChatResponse.Choice::getMessage)
                            .filter(Objects::nonNull)
                            .filter(item -> Objects.nonNull(item.getFunctionCall()))
                            .map(msg -> new AssistantMessage.ToolCall(Optional.ofNullable(msg.getFunctionStateId()).map(Objects::toString).orElse(null), "function", msg.getFunctionCall().getName(), ModelOptionsUtils.toJsonString(msg.getFunctionCall().getArguments())))
                            .toList();

                    AssistantMessage assistantMessage = new AssistantMessage(gigaChatResponse.getChoices().stream()
                            .map(GigaChatChatResponse.Choice::getMessage)
                            .filter(Objects::nonNull)
                            .map(GigaChatChatResponse.Message::getContent)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining())
                            , Map.of(), toolCalls);

                    ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
                    if (gigaChatResponse.getUsage().getPromptTokens() != null && gigaChatResponse.getUsage().getCompletionTokens() != null) {
                        generationMetadata = ChatGenerationMetadata.builder().finishReason(gigaChatResponse.getChoices().stream().reduce((first, last) -> last).orElseThrow().getFinishReason()).build();
                    }

                    var generator = new Generation(assistantMessage, generationMetadata);
                    ChatResponse chatResponse = new ChatResponse(List.of(generator), buildAnswer(gigaChatResponse, previousChatResponse));

                    observationContext.setResponse(chatResponse);

                    return chatResponse;

                });

        if (!isProxyToolCalls(prompt, this.defaultOptions) && response != null && isToolCall(response, Set.of("function_call"))) {
            var toolCallConversation = handleToolCalls(prompt, response);
            return internalCall(new Prompt(toolCallConversation, prompt.getOptions()), response);
        }

        return response;
    }

    private ChatResponseMetadata buildAnswer(GigaChatChatResponse response, ChatResponse previousResponse) {
        return ChatResponseMetadata.builder()
                .usage(from(response.getUsage(), previousResponse.getMetadata()))
                .model(response.getModel())
                .keyValue("created-at", response.getCreated())
                .build();
    }

    private Usage from(GigaChatChatResponse.Usage usage, ChatResponseMetadata metadata) {

        Long promptTokens = Long.valueOf(usage.getPromptTokens());
        Long completionTokens = Long.valueOf(usage.getCompletionTokens());
        Long totalTokens = Long.valueOf(usage.getTotalTokens());

        if (Objects.nonNull(metadata.getUsage())) {
            promptTokens += metadata.getUsage().getPromptTokens();
            completionTokens += metadata.getUsage().getGenerationTokens();
            totalTokens += metadata.getUsage().getTotalTokens();
        }

        return new DefaultUsage(promptTokens, completionTokens, totalTokens);
    }

    private ChatResponseMetadata buildAnswer(GigaChatChatResponse response) {
        Assert.notNull(response, "Ответ не может быть пустым");
        return ChatResponseMetadata.builder()
                .usage(from(response.getUsage()))
                .model(response.getModel())
                .keyValue("created-at", response.getCreated())
                .build();
    }

    private Usage from(GigaChatChatResponse.Usage usage) {
        return new DefaultUsage(Long.valueOf(usage.getPromptTokens()), Long.valueOf(usage.getCompletionTokens()), Long.valueOf(usage.getTotalTokens()));
    }

    private ChatOptions buildRequestOptions(GigaChatChatRequest request) {
        return ChatOptions.builder()
                .model(request.getModel())
                .frequencyPenalty(Optional.ofNullable(request.getRepetitionPenalty()).map(Float::doubleValue).orElse(null))
                .maxTokens(request.getMaxTokens())
                .temperature(Optional.ofNullable(request.getTemperature()).map(Float::doubleValue).orElse(null))
                .topP(Optional.ofNullable(request.getTopP()).map(Float::doubleValue).orElse(null))
                .build();
    }

    private GigaChatChatRequest buildPrompt(Prompt prompt, boolean stream) {

        List<GigaChatChatRequest.Message> messages = prompt.getInstructions().stream().map(message -> {
            if (message instanceof UserMessage userMessage) {
                var messageBuilder = GigaChatChatRequest.Message
                        .builder()
                        .role(GigaChatRole.USER)
                        .content(userMessage.getContent());
                return List.of(messageBuilder.build());
            } else if (message instanceof SystemMessage systemMessage) {
                return List.of(GigaChatChatRequest.Message.builder()
                        .role(GigaChatRole.SYSTEM)
                        .content(systemMessage.getContent()).build());
            } else if (message instanceof AssistantMessage assistantMessage) {
                List<GigaChatChatRequest.Message> toolCalls = List.of();
                if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                    toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
                        var function = new GigaChatChatRequest.FunctionCallRequest(toolCall.name(), null,
                                ModelOptionsUtils.jsonToMap(toolCall.arguments()));
                        return GigaChatChatRequest.Message.builder()
                                .role(GigaChatRole.ASSISTANT)
                                .content(assistantMessage.getContent())
                                .functionStateId(Optional.ofNullable(toolCall.id()).map(UUID::fromString).orElse(null))
                                .functionCall(function)
                                .build();
                    }).toList();
                }
                return toolCalls;
            } else if (message instanceof ToolResponseMessage toolMessage) {
                return toolMessage.getResponses()
                        .stream()
                        .map(tr -> GigaChatChatRequest.Message.builder().role(GigaChatRole.FUNCTION).content(tr.responseData()).name(tr.name()).build())
                        .toList();
            }
            throw new IllegalArgumentException("Неподдерживаемый тип: " + message.getMessageType());
        }).flatMap(List::stream).toList();

        messages = new ArrayList<>(messages);
        GigaChatChatRequest.Message sm = messages.stream().filter(m -> m.getRole() == GigaChatRole.SYSTEM).findFirst().orElse(null);

        if (Objects.nonNull(sm)) {
            messages.remove(sm);
            messages.add(0, sm);
        }

        Set<String> functionsForThisRequest = new HashSet<>();
        GigaChatChatOptions runtimeOptions = null;
        if (prompt.getOptions() != null) {
            if (prompt.getOptions() instanceof FunctionCallingOptions functionCallingOptions) {
                runtimeOptions = ModelOptionsUtils.copyToTarget(functionCallingOptions, FunctionCallingOptions.class, GigaChatChatOptions.class);
            } else {
                runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class, GigaChatChatOptions.class);
            }
            functionsForThisRequest.addAll(this.runtimeFunctionCallbackConfigurations(runtimeOptions));
        }

        if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
            functionsForThisRequest.addAll(this.defaultOptions.getFunctions());
        }

        GigaChatChatOptions mergedOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions, GigaChatChatOptions.class);


        if (!StringUtils.hasText(mergedOptions.getModel())) {
            throw new IllegalArgumentException("Модель не установлена!");
        }

        String model = mergedOptions.getModel();
        GigaChatChatRequest.GigaChatChatRequestBuilder requestBuilder = GigaChatChatRequest.builder()
                .model(model)
                .stream(stream)
                .messages(messages)
                .repetitionPenalty(mergedOptions.getFrequencyPenaltyF())
                .topP(mergedOptions.getTopPF())
                .maxTokens(mergedOptions.getMaxTokens())
                .functionCall(functionsForThisRequest.isEmpty() ? "none" : "auto")
                .updateInterval(mergedOptions.getUpdateInterval())
                .temperature(mergedOptions.getTemperatureF());

        if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
            requestBuilder.functions(getFunctionTools(functionsForThisRequest));
        }
        return requestBuilder.build();
    }

    private Collection<GigaChatChatRequest.Function> getFunctionTools(Set<String> functionNames) {
        return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> new GigaChatChatRequest.Function(functionCallback.getName(), functionCallback.getDescription(),
                ModelOptionsUtils.jsonToMap(functionCallback.getInputTypeSchema()), null, null)).toList();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions.toBuilder().build();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return ChatModel.super.stream(prompt);
    }
}

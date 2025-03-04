package org.springframework.ai.gigachat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import lombok.Setter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
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
import org.springframework.ai.model.tool.LegacyToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
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

public class GigaChatChatModel implements ChatModel {

    private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();
    private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

    private final GigaChatApi chatApi;
    private final GigaChatChatOptions defaultOptions;
    private final ObservationRegistry observationRegistry;
    private final ToolCallingManager toolCallingManager;

    @Setter
    private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    public GigaChatChatModel(GigaChatApi chatApi, GigaChatChatOptions defaultOptions, FunctionCallbackResolver functionCallbackContext, List<FunctionCallback> toolFunctionCallbacks, ObservationRegistry observationRegistry) {
        this(chatApi, defaultOptions, new LegacyToolCallingManager(functionCallbackContext, toolFunctionCallbacks), observationRegistry);
    }

    public GigaChatChatModel(GigaChatApi chatApi, GigaChatChatOptions defaultOptions, ToolCallingManager toolCallingManager, ObservationRegistry observationRegistry) {
        this.toolCallingManager = Optional.ofNullable(toolCallingManager).orElse(DEFAULT_TOOL_CALLING_MANAGER);
        this.chatApi = chatApi;
        this.defaultOptions = defaultOptions;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return internalCall(prompt, null);
    }

    private ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {
        GigaChatChatRequest request = buildPrompt(prompt, false);
        ChatModelObservationContext observationContext = createObservationContext(prompt, request);

        ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                .observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry)
                .observe(() -> {
                    GigaChatChatResponse gigaChatResponse = chatApi.chat(request);

                    List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(gigaChatResponse);
                    AssistantMessage assistantMessage = createAssistantMessage(gigaChatResponse, toolCalls);
                    ChatGenerationMetadata generationMetadata = createGenerationMetadata(gigaChatResponse);

                    var generator = new Generation(assistantMessage, generationMetadata);
                    ChatResponse chatResponse = buildChatResponse(previousChatResponse, generator, gigaChatResponse);
                    observationContext.setResponse(chatResponse);
                    return chatResponse;
                });


        if (ToolCallingChatOptions.isInternalToolExecutionEnabled(prompt.getOptions()) && Objects.nonNull(response) && response.hasToolCalls()) {
            var toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
            if (toolExecutionResult.returnDirect()) {
                return ChatResponse
                        .builder()
                        .from(response)
                        .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                        .build();
            } else {
                return this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()), response);
            }
        }

        return response;
    }

    private ChatGenerationMetadata createGenerationMetadata(GigaChatChatResponse gigaChatResponse) {
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
        if (Objects.nonNull(gigaChatResponse.getUsage()) && Objects.nonNull(gigaChatResponse.getUsage().getPromptTokens()) && Objects.nonNull(gigaChatResponse.getUsage().getCompletionTokens())) {
            generationMetadata = ChatGenerationMetadata
                    .builder()
                    .finishReason(gigaChatResponse
                            .getChoices()
                            .stream()
                            .reduce((first, last) -> last)
                            .orElseThrow()
                            .getFinishReason())
                    .build();
        }
        return generationMetadata;
    }

    private ChatResponse buildChatResponse(ChatResponse previousChatResponse, Generation generator, GigaChatChatResponse gigaChatResponse) {
        return new ChatResponse(List.of(generator), buildAnswer(gigaChatResponse, previousChatResponse));
    }

    private ChatModelObservationContext createObservationContext(Prompt prompt, GigaChatChatRequest request) {
        return ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider(GigaChatApi.PROVIDER_NAME)
                .requestOptions(buildRequestOptions(request))
                .build();
    }

    private AssistantMessage createAssistantMessage(GigaChatChatResponse gigaChatResponse, List<AssistantMessage.ToolCall> toolCalls) {
        return new AssistantMessage(gigaChatResponse.getChoices().stream()
                .map(item -> Optional.ofNullable(item.getMessage()).orElseGet(item::getDelta))
                .filter(Objects::nonNull)
                .map(GigaChatChatResponse.Message::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining())
                , Map.of(), toolCalls);
    }

    private List<AssistantMessage.ToolCall> extractToolCalls(GigaChatChatResponse gigaChatResponse) {
        return gigaChatResponse.getChoices().stream()
                .map(item -> Optional.ofNullable(item.getMessage()).orElseGet(item::getDelta))
                .filter(Objects::nonNull)
                .filter(item -> Objects.nonNull(item.getFunctionCall()))
                .map(msg -> new AssistantMessage.ToolCall(Optional.ofNullable(msg.getFunctionStateId()).map(Objects::toString).orElse(null), "function", msg.getFunctionCall().getName(), ModelOptionsUtils.toJsonString(msg.getFunctionCall().getArguments())))
                .toList();
    }

    private ChatResponseMetadata buildAnswer(GigaChatChatResponse response, ChatResponse previousResponse) {
        Usage usage = Optional.ofNullable(previousResponse)
                .map(prev -> from(response.getUsage(), prev.getMetadata()))
                .orElseGet(() -> from(response.getUsage()));
        return ChatResponseMetadata.builder()
                .usage(usage)
                .model(response.getModel())
                .keyValue("created-at", response.getCreated())
                .build();
    }

    private Usage from(GigaChatChatResponse.Usage usage, ChatResponseMetadata metadata) {

        Integer promptTokens = Optional.ofNullable(usage).map(GigaChatChatResponse.Usage::getPromptTokens).orElse(0);
        Integer completionTokens = Optional.ofNullable(usage).map(GigaChatChatResponse.Usage::getCompletionTokens).orElse(0);
        Integer totalTokens = Optional.ofNullable(usage).map(GigaChatChatResponse.Usage::getTotalTokens).orElse(0);

        if (Objects.nonNull(metadata.getUsage())) {
            promptTokens += metadata.getUsage().getPromptTokens();
            completionTokens += metadata.getUsage().getCompletionTokens();
            totalTokens += metadata.getUsage().getTotalTokens();
        }

        return new DefaultUsage(promptTokens, completionTokens, totalTokens);
    }

    private Usage from(GigaChatChatResponse.Usage usage) {
        if (Objects.isNull(usage)) {
            return new DefaultUsage(0, 0, 0);
        }
        return new DefaultUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private ChatOptions buildRequestOptions(GigaChatChatRequest request) {
        return ChatOptions.builder()
                .model(request.getModel())
                .frequencyPenalty(request.getRepetitionPenalty())
                .maxTokens(request.getMaxTokens())
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .build();
    }

    private GigaChatChatRequest buildPrompt(Prompt prompt, boolean stream) {
        List<GigaChatChatRequest.Message> messages = prompt.getInstructions().stream().map(GigaChatChatModel::convertMessage).flatMap(List::stream).toList();

        messages = new ArrayList<>(messages);
        GigaChatChatRequest.Message sm = messages.stream().filter(m -> m.getRole() == GigaChatRole.SYSTEM).findFirst().orElse(null);

        if (Objects.nonNull(sm)) {
            messages.remove(sm);
            messages.add(0, sm);
        }

        Set<String> functionsForThisRequest = new HashSet<>();
        GigaChatChatOptions runtimeOptions = getRuntimeOptions(prompt, functionsForThisRequest);

        if (!CollectionUtils.isEmpty(defaultOptions.getFunctions())) {
            functionsForThisRequest.addAll(defaultOptions.getFunctions());
        }

        GigaChatChatOptions mergedOptions = ModelOptionsUtils.merge(runtimeOptions, defaultOptions, GigaChatChatOptions.class);
        mergedOptions.setInternalToolExecutionEnabled(ModelOptionsUtils.mergeOption(runtimeOptions.isInternalToolExecutionEnabled(), defaultOptions.isInternalToolExecutionEnabled()));
        mergedOptions.setToolCallbacks(ModelOptionsUtils.mergeOption(runtimeOptions.getToolCallbacks(), defaultOptions.getToolCallbacks()));
        mergedOptions.setToolNames(ModelOptionsUtils.mergeOption(runtimeOptions.getToolNames(), defaultOptions.getToolNames()));
        mergedOptions.setToolContext(ModelOptionsUtils.mergeOption(runtimeOptions.getToolContext(), defaultOptions.getToolContext()));

        if (!StringUtils.hasText(mergedOptions.getModel())) {
            throw new IllegalArgumentException("Модель не установлена!");
        }

        return buildRequest(stream, mergedOptions, messages, functionsForThisRequest);
    }

    private GigaChatChatRequest buildRequest(boolean stream, GigaChatChatOptions mergedOptions, List<GigaChatChatRequest.Message> messages, Set<String> functionsForThisRequest) {
        String model = mergedOptions.getModel();
        GigaChatChatRequest.GigaChatChatRequestBuilder requestBuilder = GigaChatChatRequest.builder()
                .model(model)
                .stream(stream)
                .messages(messages)
                .repetitionPenalty(mergedOptions.getFrequencyPenalty())
                .topP(mergedOptions.getTopP())
                .maxTokens(mergedOptions.getMaxTokens())
                .functionCall(functionsForThisRequest.isEmpty() ? "none" : "auto")
                .updateInterval(mergedOptions.getUpdateInterval())
                .temperature(mergedOptions.getTemperature());

        if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
            requestBuilder.functions(getFunctionTools(mergedOptions, functionsForThisRequest));
        }
        return requestBuilder.build();
    }

    private GigaChatChatOptions getRuntimeOptions(Prompt prompt, Set<String> functionsForThisRequest) {
        GigaChatChatOptions runtimeOptions = null;
        if (Objects.nonNull(prompt.getOptions())) {
            if (prompt.getOptions() instanceof FunctionCallingOptions functionCallingOptions) {
                runtimeOptions = ModelOptionsUtils.copyToTarget(functionCallingOptions, FunctionCallingOptions.class, GigaChatChatOptions.class);
            } else if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
                runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class, GigaChatChatOptions.class);
            } else {
                runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class, GigaChatChatOptions.class);
            }
            if (!CollectionUtils.isEmpty(runtimeOptions.getToolCallbacks())) {
                functionsForThisRequest.addAll(runtimeOptions.getToolCallbacks().stream().map(FunctionCallback::getName).collect(Collectors.toSet()));
                runtimeOptions.setToolNames(functionsForThisRequest);
            }
        }
        return runtimeOptions;
    }

    private static List<GigaChatChatRequest.Message> convertMessage(Message message) {
        if (message instanceof UserMessage userMessage) {
            return createUserMessage(userMessage);
        } else if (message instanceof SystemMessage systemMessage) {
            return createSystemMessage(systemMessage);
        } else if (message instanceof AssistantMessage assistantMessage) {
            return createAssistantMessage(assistantMessage);
        } else if (message instanceof ToolResponseMessage toolMessage) {
            return createToolResponseMessage(toolMessage);
        }
        throw new IllegalArgumentException("Неподдерживаемый тип: " + message.getMessageType());
    }

    private static List<GigaChatChatRequest.Message> createToolResponseMessage(ToolResponseMessage toolMessage) {
        return toolMessage.getResponses()
                .stream()
                .map(tr -> GigaChatChatRequest.Message.builder().role(GigaChatRole.FUNCTION).content(tr.responseData()).name(tr.name()).build())
                .toList();
    }

    private static List<GigaChatChatRequest.Message> createAssistantMessage(AssistantMessage assistantMessage) {
        List<GigaChatChatRequest.Message> toolCalls = List.of();
        if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
            toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> GigaChatChatRequest.Message
                    .builder()
                    .role(GigaChatRole.ASSISTANT)
                    .content(assistantMessage.getText())
                    .functionStateId(Optional.ofNullable(toolCall.id()).map(UUID::fromString).orElse(null))
                    .functionCall(new GigaChatChatRequest.FunctionCallRequest(
                            toolCall.name(),
                            null,
                            ModelOptionsUtils.jsonToMap(toolCall.arguments())))
                    .build()).toList();

            return toolCalls;
        }

        return List.of(GigaChatChatRequest.Message
                .builder()
                .role(GigaChatRole.ASSISTANT)
                .content(assistantMessage.getText())
                .build());
    }

    private static List<GigaChatChatRequest.Message> createSystemMessage(SystemMessage systemMessage) {
        return List.of(GigaChatChatRequest.Message.builder()
                .role(GigaChatRole.SYSTEM)
                .content(systemMessage.getText()).build());
    }

    private static List<GigaChatChatRequest.Message> createUserMessage(UserMessage userMessage) {
        return List.of(GigaChatChatRequest.Message
                .builder()
                .role(GigaChatRole.USER)
                .content(userMessage.getText())
                .build());
    }

    private Collection<GigaChatChatRequest.Function> getFunctionTools(GigaChatChatOptions mergedOptions, Set<String> functionNames) {
        return toolCallingManager.resolveToolDefinitions(mergedOptions)
                .stream()
                .filter(item -> functionNames.contains(item.name()))
                .map(toolDefinition -> new GigaChatChatRequest.Function(
                        toolDefinition.name(),
                        toolDefinition.description(),
                        ModelOptionsUtils.jsonToMap(toolDefinition.inputSchema()),
                        null,
                        null))
                .toList();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions.toBuilder().build();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return internalStream(prompt, null);
    }

    private Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
        return Flux.deferContextual(view -> {
            GigaChatChatRequest request = buildPrompt(prompt, true);
            ChatModelObservationContext observationContext = createObservationContext(prompt, request);

            Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry);
            observation.parentObservation(Objects.requireNonNull(view.getOrDefault(ObservationThreadLocalAccessor.KEY, null))).start();

            Flux<GigaChatChatResponse> gigaChatResponse = chatApi.streamingChat(request);

            Flux<ChatResponse> chatResponse = gigaChatResponse.map(part -> {
                List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(part);
                AssistantMessage assistantMessage = createAssistantMessage(part, toolCalls);
                ChatGenerationMetadata generationMetadata = createGenerationMetadata(part);

                var generator = new Generation(assistantMessage, generationMetadata);
                return buildChatResponse(previousChatResponse, generator, part);
            });

            return chatResponse.flatMap(response -> {
                if (ToolCallingChatOptions.isInternalToolExecutionEnabled(prompt.getOptions()) && Objects.nonNull(response) && response.hasToolCalls()) {
                    var toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
                    if (toolExecutionResult.returnDirect()) {
                        return Flux.just(ChatResponse
                                .builder()
                                .from(response)
                                .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                                .build()
                        );
                    } else {
                        return this.internalStream(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()), response);
                    }
                }
                return Flux.just(response);
            });
        });
    }
}

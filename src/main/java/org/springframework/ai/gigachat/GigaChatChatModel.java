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

/**
 * Implementation of the {@link ChatModel} interface using GigaChat API.
 */
public class GigaChatChatModel implements ChatModel {

    /**
     * Default observation convention used for chat model operations.
     */
    private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

    /**
     * Default tool calling manager used when none is provided.
     */
    private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

    /**
     * The GigaChat API client used to interact with the GigaChat service.
     */
    private final GigaChatApi chatApi;

    /**
     * Default options for chat operations.
     */
    private final GigaChatChatOptions defaultOptions;

    /**
     * Registry for observations, which is used for monitoring and logging purposes.
     */
    private final ObservationRegistry observationRegistry;

    /**
     * Manager responsible for handling tool calls in the chat model.
     */
    private final ToolCallingManager toolCallingManager;

    /**
     * Observation convention to be used for this chat model instance.
     * Can be set by external clients.
     */
    @Setter
    private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    /**
     * Constructs a new {@link GigaChatChatModel} using the provided API client,
     * default options, function callback context, tool function callbacks, and observation registry.
     *
     * @param chatApi               The GigaChat API client.
     * @param defaultOptions        Default options for chat operations.
     * @param functionCallbackContext Context for resolving function callbacks.
     * @param toolFunctionCallbacks List of tool function callbacks.
     * @param observationRegistry   Registry for observations.
     */
    public GigaChatChatModel(GigaChatApi chatApi, GigaChatChatOptions defaultOptions, FunctionCallbackResolver functionCallbackContext, List<FunctionCallback> toolFunctionCallbacks, ObservationRegistry observationRegistry) {
        this(chatApi, defaultOptions, new LegacyToolCallingManager(functionCallbackContext, toolFunctionCallbacks), observationRegistry);
    }

    /**
     * Constructs a new {@link GigaChatChatModel} using the provided API client,
     * default options, tool calling manager, and observation registry.
     *
     * @param chatApi         The GigaChat API client.
     * @param defaultOptions  Default options for chat operations.
     * @param toolCallingManager Manager responsible for handling tool calls.
     * @param observationRegistry Registry for observations.
     */
    public GigaChatChatModel(GigaChatApi chatApi, GigaChatChatOptions defaultOptions, ToolCallingManager toolCallingManager, ObservationRegistry observationRegistry) {
        this.toolCallingManager = Optional.ofNullable(toolCallingManager).orElse(DEFAULT_TOOL_CALLING_MANAGER);
        this.chatApi = chatApi;
        this.defaultOptions = defaultOptions;
        this.observationRegistry = observationRegistry;
    }

    /**
     * Calls the chat API with the given prompt and returns a single {@link ChatResponse}.
     */
    @Override
    public ChatResponse call(Prompt prompt) {
        return internalCall(prompt, null);
    }

    /**
     * Internal method to handle the actual call to the GigaChat API.
     *
     * @param prompt             The user's input prompt.
     * @param previousChatResponse Previous chat response, if available.
     * @return A {@link ChatResponse} containing the assistant's reply and any associated metadata.
     */
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


        if (ToolCallingChatOptions.isInternalToolExecutionEnabled(prompt.getOptions()) && response.hasToolCalls()) {
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

    /**
     * Creates the metadata for a generated response.
     *
     * @param gigaChatResponse The GigaChat API response.
     * @return A {@link ChatGenerationMetadata} instance containing metadata about the generation process.
     */
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

    /**
     * Builds a chat response from the generated data and previous response (if any).
     *
     * @param previousChatResponse The previous {@link ChatResponse} in the conversation.
     * @param generator            The new generation to add to the response.
     * @param gigaChatResponse     The raw GigaChat API response.
     * @return A complete {@link ChatResponse}.
     */
    private ChatResponse buildChatResponse(ChatResponse previousChatResponse, Generation generator, GigaChatChatResponse gigaChatResponse) {
        return new ChatResponse(List.of(generator), buildAnswer(gigaChatResponse, previousChatResponse));
    }

    /**
     * Creates an observation context for the given prompt and request.
     *
     * @param prompt The user's input prompt.
     * @param request The built GigaChat API request.
     * @return A {@link ChatModelObservationContext} instance containing details about the current operation.
     */
    private ChatModelObservationContext createObservationContext(Prompt prompt, GigaChatChatRequest request) {
        return ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider(GigaChatApi.PROVIDER_NAME)
                .requestOptions(buildRequestOptions(request))
                .build();
    }

    /**
     * Creates an assistant message from the response data.
     *
     * @param gigaChatResponse The GigaChat API response.
     * @param toolCalls        List of tool calls extracted from the response.
     * @return An {@link AssistantMessage} containing the assistant's reply and any associated tool calls.
     */
    private AssistantMessage createAssistantMessage(GigaChatChatResponse gigaChatResponse, List<AssistantMessage.ToolCall> toolCalls) {
        return new AssistantMessage(gigaChatResponse.getChoices().stream()
                .map(item -> Optional.ofNullable(item.getMessage()).orElseGet(item::getDelta))
                .filter(Objects::nonNull)
                .map(GigaChatChatResponse.Message::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining())
                , Map.of(), toolCalls);
    }

    /**
     * Extracts tool calls from the GigaChat API response.
     *
     * @param gigaChatResponse The GigaChat API response.
     * @return A list of {@link AssistantMessage.ToolCall} instances representing tool calls made during the conversation.
     */
    private List<AssistantMessage.ToolCall> extractToolCalls(GigaChatChatResponse gigaChatResponse) {
        return gigaChatResponse.getChoices().stream()
                .map(item -> Optional.ofNullable(item.getMessage()).orElseGet(item::getDelta))
                .filter(Objects::nonNull)
                .filter(item -> Objects.nonNull(item.getFunctionCall()))
                .map(msg -> new AssistantMessage.ToolCall(Optional.ofNullable(msg.getFunctionStateId()).map(Objects::toString).orElse(null), "function", msg.getFunctionCall().getName(), ModelOptionsUtils.toJsonString(msg.getFunctionCall().getArguments())))
                .toList();
    }

    /**
     * Builds the response metadata based on the GigaChat API response and previous response (if any).
     *
     * @param response The GigaChat API response.
     * @param previousResponse Previous chat response, if available.
     * @return A {@link ChatResponseMetadata} instance containing usage information and other metadata.
     */
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

    /**
     * Merges usage information from two responses.
     *
     * @param usage      Usage information from the current API response.
     * @param metadata The previous response's metadata containing usage info.
     * @return A {@link Usage} instance representing the merged usage statistics.
     */
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

    /**
     * Converts the usage information from the GigaChat API response to a {@link Usage} instance.
     *
     * @param usage The usage information from the API response.
     * @return A {@link Usage} instance representing the token counts.
     */
    private Usage from(GigaChatChatResponse.Usage usage) {
        if (Objects.isNull(usage)) {
            return new DefaultUsage(0, 0, 0);
        }
        return new DefaultUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /**
     * Converts the request options from a {@link GigaChatChatRequest} to a {@link ChatOptions} instance.
     *
     * @param request The request object containing various parameters.
     * @return A {@link ChatOptions} instance representing the same set of parameters.
     */
    private ChatOptions buildRequestOptions(GigaChatChatRequest request) {
        return ChatOptions.builder()
                .model(request.getModel())
                .frequencyPenalty(request.getRepetitionPenalty())
                .maxTokens(request.getMaxTokens())
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .build();
    }

    /**
     * Converts the user prompt and options into a {@link GigaChatChatRequest}.
     *
     * @param prompt The user's input prompt.
     * @param stream Flag indicating whether to enable streaming.
     * @return A {@link GigaChatChatRequest} instance representing the request parameters.
     */
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

    /**
     * Constructs a GigaChat API request based on the specified options and parameters.
     *
     * @param stream   Flag indicating whether to enable streaming.
     * @param mergedOptions The final options for the request after merging default and runtime settings.
     * @param messages List of messages (converted from user input).
     * @param functionsForThisRequest Set of function names enabled in this request.
     * @return A {@link GigaChatChatRequest} instance representing the constructed API request.
     */
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

    /**
     * Retrieves runtime options from the user prompt.
     *
     * @param prompt The user's input prompt.
     * @param functionsForThisRequest Set to accumulate names of functions specified in this request.
     * @return A {@link GigaChatChatOptions} instance containing the resolved runtime options.
     */
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

    /**
     * Converts a {@link Message} instance to a list of GigaChat API messages.
     *
     * @param message The input message.
     * @return A list of {@link GigaChatChatRequest.Message} objects representing the converted message.
     */
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

    /**
     * Converts a {@link ToolResponseMessage} to a list of GigaChat API messages.
     *
     * @param toolMessage The input tool response message.
     * @return A list of {@link GigaChatChatRequest.Message} objects representing the converted tool response.
     */
    private static List<GigaChatChatRequest.Message> createToolResponseMessage(ToolResponseMessage toolMessage) {
        return toolMessage.getResponses()
                .stream()
                .map(tr -> GigaChatChatRequest.Message.builder().role(GigaChatRole.FUNCTION).content(tr.responseData()).name(tr.name()).build())
                .toList();
    }

    /**
     * Converts an {@link AssistantMessage} to a list of GigaChat API messages.
     *
     * @param assistantMessage The input assistant message.
     * @return A list of {@link GigaChatChatRequest.Message} objects representing the converted assistant message.
     */
    private static List<GigaChatChatRequest.Message> createAssistantMessage(AssistantMessage assistantMessage) {
        List<GigaChatChatRequest.Message> toolCalls;
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

    /**
     * Converts a {@link SystemMessage} to a list of GigaChat API messages.
     *
     * @param systemMessage The input system message.
     * @return A list of {@link GigaChatChatRequest.Message} objects representing the converted system message.
     */
    private static List<GigaChatChatRequest.Message> createSystemMessage(SystemMessage systemMessage) {
        return List.of(GigaChatChatRequest.Message.builder()
                .role(GigaChatRole.SYSTEM)
                .content(systemMessage.getText()).build());
    }

    /**
     * Converts a {@link UserMessage} to a list of GigaChat API messages.
     *
     * @param userMessage The input user message.
     * @return A list of {@link GigaChatChatRequest.Message} objects representing the converted user message.
     */
    private static List<GigaChatChatRequest.Message> createUserMessage(UserMessage userMessage) {
        return List.of(GigaChatChatRequest.Message
                .builder()
                .role(GigaChatRole.USER)
                .content(userMessage.getText())
                .build());
    }

    /**
     * Retrieves function tools based on specified options and names.
     *
     * @param mergedOptions The final request options after merging default and runtime settings.
     * @param functionNames Set of function names enabled in this request.
     * @return A collection of {@link GigaChatChatRequest.Function} representing the resolved function definitions.
     */
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

    /**
     * Retrieves the default options configured for this chat model.
     *
     * @return A {@link ChatOptions} instance containing the default settings.
     */
    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions.toBuilder().build();
    }

    /**
     * Streams the chat response based on the user prompt.
     *
     * @param prompt The user's input prompt.
     * @return A {@link Flux} emitting chat responses as they are generated.
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return internalStream(prompt, null);
    }

    /**
     * Internally handles the streaming of chat responses with optional previous response context.
     *
     * @param prompt The user's input prompt.
     * @param previousChatResponse Optional previous chat response to provide context.
     * @return A {@link Flux} emitting chat responses as they are generated.
     */
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

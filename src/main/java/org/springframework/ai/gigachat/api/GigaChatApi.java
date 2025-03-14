package org.springframework.ai.gigachat.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ai.gigachat.api.model.GigaChatChatRequest;
import org.springframework.ai.gigachat.api.model.GigaChatChatResponse;
import org.springframework.ai.gigachat.api.model.GigaChatEmbeddingRequest;
import org.springframework.ai.gigachat.api.model.GigaChatEmbeddingResponse;
import org.springframework.ai.gigachat.api.model.GigaChatOAuthResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * API class for interacting with GigaChat services.
 */
public class GigaChatApi {
    public static final String PROVIDER_NAME = "gigachat";
    public static final String REQUEST_BODY_NULL_ERROR = "Тело запроса не может быть пустым.";
    private static final Log logger = LogFactory.getLog(GigaChatApi.class);
    private final ResponseErrorHandler responseErrorHandler;
    private final RestClient restClient;
    private final WebClient webClient;
    private final Consumer<HttpHeaders> defaultHeaders;

    /**
     * Constructs a new GigaChatApi instance.
     *
     * @param baseUrl The base URL for the API.
     * @param authUrl The authentication URL.
     * @param scope   The OAuth scope.
     * @param clientId The client ID for authentication.
     * @param secret   The client secret for authentication.
     */
    public GigaChatApi(String baseUrl, String authUrl, Scope scope, String clientId, String secret) {
        this(baseUrl, clientId, new ApiKeySupplier(scope, clientId, secret, RestClient.builder().baseUrl(authUrl).clone()), RestClient.builder(), WebClient.builder());
    }

    /**
     * Constructs a new GigaChatApi instance with custom RestClient and WebClient builders.
     *
     * @param baseUrl         The base URL for the API.
     * @param authUrl         The authentication URL.
     * @param scope           The OAuth scope.
     * @param clientId        The client ID for authentication.
     * @param secret          The client secret for authentication.
     * @param restClientBuilder The builder for RestClient.
     * @param webClientBuilder  The builder for WebClient.
     */
    public GigaChatApi(String baseUrl, String authUrl, Scope scope, String clientId, String secret, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        this(baseUrl, clientId, new ApiKeySupplier(scope, clientId, secret, restClientBuilder.baseUrl(authUrl).clone()), restClientBuilder, webClientBuilder);
    }

    /**
     * Constructs a new GigaChatApi instance with a custom API key supplier.
     *
     * @param baseUrl          The base URL for the API.
     * @param clientId         The client ID for authentication.
     * @param apiKeySupplier   Supplier of the API key.
     * @param restClientBuilder The builder for RestClient.
     * @param webClientBuilder  The builder for WebClient.
     */
    public GigaChatApi(String baseUrl, String clientId, Supplier<String> apiKeySupplier, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        this.responseErrorHandler = new GigaChataResponseErrorHandler();

        String xSession = UUID.randomUUID().toString();

      defaultHeaders = headers -> {
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setBearerAuth(apiKeySupplier.get());
            headers.set("X-Client-ID", clientId);
            headers.set("X-Session-ID", xSession);
            headers.set("X-Request-ID", UUID.randomUUID().toString());
        };

        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Sends a chat request to the GigaChat API.
     *
     * @param chatRequest The chat request object.
     * @return The response from the GigaChat API.
     */
    public GigaChatChatResponse chat(GigaChatChatRequest chatRequest) {
        Assert.notNull(chatRequest, REQUEST_BODY_NULL_ERROR);
        Assert.isTrue(!chatRequest.getStream(), "Потоковая обработка должна быть выключена.");

        return this.restClient.post()
                .uri("/api/v1/chat/completions")
                .body(chatRequest)
                .headers(defaultHeaders)
                .retrieve()
                .onStatus(this.responseErrorHandler)
                .body(GigaChatChatResponse.class);
    }

    /**
     * Sends a streaming chat request to the GigaChat API.
     *
     * @param chatRequest The chat request object.
     * @return A Flux of responses from the GigaChat API.
     */
    public Flux<GigaChatChatResponse> streamingChat(GigaChatChatRequest chatRequest) {
        Assert.notNull(chatRequest, REQUEST_BODY_NULL_ERROR);
        Assert.isTrue(chatRequest.getStream(), "Потоковая обработка должна быть включена.");

        return this.webClient.post()
                .uri("/api/v1/chat/completions")
                .body(Mono.just(chatRequest), GigaChatChatRequest.class)
                .headers(defaultHeaders)
                .retrieve()
                .bodyToFlux(String.class)
                .takeUntil("[DONE]"::equals)
                .filter(item -> !"[DONE]".equals(item))
                .map(item -> ModelOptionsUtils.jsonToObject(item, GigaChatChatResponse.class))
                .handle((data, sink) -> {
                    if (logger.isTraceEnabled()) {
                        logger.trace(data);
                    }
                    sink.next(data);
                });
    }

    /**
     * Sends an embedding request to the GigaChat API.
     *
     * @param embeddingsRequest The embedding request object.
     * @return The response from the GigaChat API.
     */
    public GigaChatEmbeddingResponse embed(GigaChatEmbeddingRequest embeddingsRequest) {
        Assert.notNull(embeddingsRequest, REQUEST_BODY_NULL_ERROR);

        return this.restClient.post()
                .uri("/api/v1/embeddings")
                .body(embeddingsRequest)
                .headers(defaultHeaders)
                .retrieve()
                .onStatus(this.responseErrorHandler)
                .body(GigaChatEmbeddingResponse.class);
    }

    /**
     * Custom ResponseErrorHandler for the GigaChat API.
     */
    private static class GigaChataResponseErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return response.getStatusCode().isError();
        }

        @Override
        public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
            if (response.getStatusCode().isError()) {
                int statusCode = response.getStatusCode().value();
                String statusText = response.getStatusText();
                String message = StreamUtils.copyToString(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                logger.warn(String.format("[%s] %s - %s", statusCode, statusText, message));
                throw new RuntimeException(String.format("[%s] %s - %s", statusCode, statusText, message));
            }
        }
    }

    /**
     * Supplier class for managing API keys.
     */
    public static class ApiKeySupplier implements Supplier<String> {
        private static final long tokenUpdateInterval = TimeUnit.of(ChronoUnit.MINUTES).toMillis(1);
        private final Scope scope;
        private final RestClient oauthRestClient;
        private String apiKey;
        private long apiKeyExpiresAt;
        private final ResponseErrorHandler responseErrorHandler;
        private final  Consumer<HttpHeaders> oauthHeaders;

        /**
         * Constructs a new ApiKeySupplier instance.
         *
         * @param scope    The OAuth scope.
         * @param clientId The client ID for authentication.
         * @param secret   The client secret for authentication.
         * @param restClient The builder for RestClient.
         */
        public ApiKeySupplier(Scope scope, String clientId, String secret, RestClient.Builder restClient) {
            this.scope = scope;
            this.responseErrorHandler = new GigaChataResponseErrorHandler();

            oauthHeaders = headers -> {
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                headers.setBasicAuth(clientId, secret);
                headers.set("RqUID", UUID.randomUUID().toString());
            };


            this.oauthRestClient = restClient.messageConverters(c -> c.add(new FormHttpMessageConverter())).build();
        }

        @Override
        public synchronized String get() {
            if (StringUtils.hasText(apiKey) && apiKeyExpiresAt - tokenUpdateInterval > Instant.now().toEpochMilli()) {
                return apiKey;
            }

            MultiValueMap<String, String> req = new LinkedMultiValueMap<>();
            req.add("scope", scope.name());


            GigaChatOAuthResponse response = oauthRestClient.post().uri("/api/v2/oauth").headers(oauthHeaders).body(req).retrieve().onStatus(responseErrorHandler).body(GigaChatOAuthResponse.class);
            apiKey = response.getAccessToken();
            apiKeyExpiresAt = response.getExpiresAt();

            return apiKey;
        }
    }

    /**
     * Enum representing different OAuth scopes for GigaChat.
     */
    public enum Scope {
        GIGACHAT_API_PERS,
        GIGACHAT_API_B2B,
        GIGACHAT_API_CORP
    }
}

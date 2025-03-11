# Sber GigaChat integration module for Spring AI

[![](https://jitpack.io/v/DenisLAD/spring-ai-gigachat.svg)](https://jitpack.io/#DenisLAD/spring-ai-gigachat) ![Weekly download statistics](https://jitpack.io/v/DenisLAD/spring-ai-gigachat/week.svg) ![Monthly download statistics](https://jitpack.io/v/DenisLAD/spring-ai-gigachat/month.svg) [![](https://github.com/DenisLAD/spring-ai-gigachat/actions/workflows/maven.yml/badge.svg)](https://github.com/DenisLAD/spring-ai-gigachat/actions/workflows/maven.yml) [![](https://github.com/DenisLAD/spring-ai-gigachat/actions/workflows/maven-publish.yml/badge.svg)](https://github.com/DenisLAD/spring-ai-gigachat/actions/workflows/maven-publish.yml)

[GigaChat API Documentation](https://developers.sber.ru/portal/products/gigachat-api)

[Spring AI Documentation](https://docs.spring.io/spring-ai/reference/index.html)

## Usage

> [!NOTE]  
> `spring-ai-gigachat` is compatible with `spring-boot-ai:1.0.0-M6`
>
> More info: [Migrating from FunctionCallback to ToolCallback API](https://docs.spring.io/spring-ai/reference/api/tools-migration.html)


### Add repository to your `pom.xml` file

```xml
<repositories>
    <repository>
        <id>central</id>
        <url>https://repo1.maven.org/maven2</url>
    </repository>
    <repository>
        <id>jitpack</id>
        <url>https://jitpack.io</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
        <releases>
            <enabled>true</enabled>
        </releases>
    </repository>
</repositories>
```

### Add dependency

```xml
<dependency>
  <groupId>com.github.DenisLAD</groupId>
  <artifactId>spring-ai-gigachat</artifactId>
  <version>0.0.6-2</version>
</dependency>
```

### Spring configuration

```java
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.gigachat.GigaChatChatModel;
import org.springframework.ai.gigachat.api.GigaChatApi;
import org.springframework.ai.gigachat.api.GigaChatChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GigaChatConfiguration.class)
@ConfigurationProperties(prefix = "spring.ai.gigachat.init")
public class GigaChatConfiguration {
    private String authUrl;
    private String chatUrl;
    private String clientId;
    private String clientSecret;
    private String model;
    private GigaChatApi.Scope scope;

    public @Bean GigaChatChatOptions chatOptions() {
        return GigaChatChatOptions.builder().model(model).build();
    }

    public @Bean GigaChatApi chatApi() {
        return new GigaChatApi(chatUrl, authUrl, scope, clientId, clientSecret);
    }

    public @Bean ChatModel chatModel(GigaChatApi api, GigaChatChatOptions options) {
        return new GigaChatChatModel(api, options, null, null, ObservationRegistry.NOOP);
    }

    public @Bean ChatClient chatClient(ChatModel chatModel, GigaChatChatOptions options) {
        return ChatClient
                .builder(chatModel)
                .defaultOptions(options)
                .defaultSystem(c -> c.text("Ты — GigaChat, русскоязычный автоматический ассистент. Ты разговариваешь с людьми и помогаешь им отвечая подробно на их вопросы."))
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(new InMemoryChatMemory()).build())
                .build();
    }

    public void setAuthUrl(String authUrl) {
        this.authUrl = authUrl;
    }

    public void setChatUrl(String chatUrl) {
        this.chatUrl = chatUrl;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setScope(GigaChatApi.Scope scope) {
        this.scope = scope;
    }
} 
```

### application.yml

```yaml
spring:
  ai:
    gigachat:
      init:
        auth-url: https://ngw.devices.sberbank.ru:9443
        chat-url: https://gigachat.devices.sberbank.ru
        client-id: "[client id here]"
        client-secret: "[client secret here]"
        model: GigaChat
        scope: GIGACHAT_API_PERS
```

### Rest service

```java
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class RestUsageExample {

    private @Autowired ChatClient chatClient;

    @GetMapping("question")
    public String question(@RequestParam("txt") String query) {
        return chatClient.prompt(query).call().chatResponse().getResult().getOutput().getContent();
    }
}
```

### BASH Example

```shell
curl --request GET --url http://localhost:8080/question?txt=Who%20are%20you%3F
```
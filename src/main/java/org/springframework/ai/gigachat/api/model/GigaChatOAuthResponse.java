package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GigaChatOAuthResponse {
    private @JsonProperty("access_token") String accessToken;
    private @JsonProperty("expires_at") Long expiresAt;
}

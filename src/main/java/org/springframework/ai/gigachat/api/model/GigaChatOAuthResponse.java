package org.springframework.ai.gigachat.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents the OAuth response received from GigaChat.
 *
 * <p>This class contains the access token and its expiration time, which are essential for authenticating subsequent API requests to GigaChat.</p>
 */
@Data
public class GigaChatOAuthResponse {
    /**
     * The access token provided by GigaChat for authentication.
     * This token is used in the Authorization header of API requests to GigaChat.
     */
    private @JsonProperty("access_token") String accessToken;

    /**
     * The timestamp indicating when the access token expires, in milliseconds since the Unix epoch (January 1, 1970).
     * After this time, a new token should be requested from GigaChat to maintain authentication.
     */
    private @JsonProperty("expires_at") Long expiresAt;
}

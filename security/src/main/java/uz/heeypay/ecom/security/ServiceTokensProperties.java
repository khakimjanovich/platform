package uz.heeypay.ecom.security;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which HTTP service groups send a client-credentials token, and from which registration ({@code
 * spring.security.oauth2.client.registration.<id>}).
 *
 * @param groups each group's name and the registration its calls get their token from
 * @param connectTimeout how long a token request waits for a connection to the token endpoint
 * @param readTimeout how long a token request waits for its answer
 */
@ConfigurationProperties(SecurityConstants.SERVICE_TOKENS_PREFIX)
public record ServiceTokensProperties(
    @DefaultValue Map<String, String> groups,
    @DefaultValue(SecurityConstants.DEFAULT_TOKEN_CONNECT_TIMEOUT) Duration connectTimeout,
    @DefaultValue(SecurityConstants.DEFAULT_TOKEN_READ_TIMEOUT) Duration readTimeout) {}

package uz.heeypay.ecom.security;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which HTTP service groups send a client-credentials token, and from which registration ({@code
 * spring.security.oauth2.client.registration.<id>}).
 *
 * @param groups each group's name and the registration its calls get their token from
 */
@ConfigurationProperties(SecurityConstants.SERVICE_TOKENS_PREFIX)
public record ServiceTokensProperties(@DefaultValue Map<String, String> groups) {}

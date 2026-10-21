package uz.heeypay.ecom.keycloak;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where Keycloak is, which realm, and the confidential client the backend logs in with.
 *
 * @param url Keycloak's base URL, e.g. {@code http://localhost:8180}; setting it turns the library
 *     on
 * @param realm the realm whose users, roles and clients are managed
 * @param clientId the backend's own confidential client, allowed to use the admin API
 * @param clientSecret that client's secret
 */
@Validated
@ConfigurationProperties(KeycloakConstants.PROPERTIES_PREFIX)
public record KeycloakProperties(
    @NotBlank String url, @NotBlank String realm, @NotBlank String clientId, String clientSecret) {}

package uz.heeypay.ecom.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where Keycloak is, which realm, and the confidential client the backend logs in with. */
@ConfigurationProperties(KeycloakConstantUtils.PROPERTIES_PREFIX)
public record KeycloakProperties(String url, String realm, String clientId, String clientSecret) {}

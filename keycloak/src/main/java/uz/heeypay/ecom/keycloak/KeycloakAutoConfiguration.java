package uz.heeypay.ecom.keycloak;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

/**
 * Adding the library and setting {@code ecom.keycloak.url} gives every module a {@link
 * KeycloakAdmin}. It logs in as its own client with client credentials and refreshes the token when
 * it expires.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = KeycloakConstants.PROPERTIES_PREFIX, name = "url")
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  KeycloakAdmin keycloakAdmin(
      KeycloakProperties keycloak, ObjectProvider<RestClient.Builder> builders) {
    var registration =
        ClientRegistration.withRegistrationId(KeycloakConstants.REGISTRATION_ID)
            .clientId(keycloak.clientId())
            .clientSecret(keycloak.clientSecret())
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(keycloak.url() + KeycloakConstants.TOKEN_PATH.formatted(keycloak.realm()))
            .build();
    var registrations = new InMemoryClientRegistrationRepository(registration);
    var login =
        new OAuth2ClientHttpRequestInterceptor(
            new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, new InMemoryOAuth2AuthorizedClientService(registrations)));
    login.setClientRegistrationIdResolver(request -> KeycloakConstants.REGISTRATION_ID);
    return new KeycloakAdmin(
        builders
            .getIfAvailable(RestClient::builder)
            .baseUrl(keycloak.url() + KeycloakConstants.ADMIN_PATH.formatted(keycloak.realm()))
            .requestInterceptor(login)
            .build());
  }
}

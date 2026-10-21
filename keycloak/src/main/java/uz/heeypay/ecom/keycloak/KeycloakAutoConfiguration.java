package uz.heeypay.ecom.keycloak;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Adding the library and setting {@code ecom.keycloak.url} gives every module a {@link
 * KeycloakAdmin}. It logs in as its own client with client credentials and refreshes the token when
 * it expires. Every call, the token request included, gives up after the configured timeouts.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = KeycloakConstants.PROPERTIES_PREFIX, name = "url")
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  KeycloakAdmin keycloakAdmin(
      KeycloakProperties keycloak, ObjectProvider<RestClient.Builder> builders) {
    var http =
        ClientHttpRequestFactoryBuilder.detect()
            .build(
                HttpClientSettings.defaults()
                    .withTimeouts(keycloak.connectTimeout(), keycloak.readTimeout()));
    var registration =
        ClientRegistration.withRegistrationId(KeycloakConstants.REGISTRATION_ID)
            .clientId(keycloak.clientId())
            .clientSecret(keycloak.clientSecret())
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(keycloak.url() + KeycloakConstants.TOKEN_PATH.formatted(keycloak.realm()))
            .build();
    var registrations = new InMemoryClientRegistrationRepository(registration);
    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
    manager.setAuthorizedClientProvider(tokens(http));
    var login = new OAuth2ClientHttpRequestInterceptor(manager);
    login.setClientRegistrationIdResolver(request -> KeycloakConstants.REGISTRATION_ID);
    return new KeycloakAdmin(
        builders
            .getIfAvailable(RestClient::builder)
            .baseUrl(keycloak.url() + KeycloakConstants.ADMIN_PATH.formatted(keycloak.realm()))
            .requestFactory(http)
            .requestInterceptor(login)
            .build());
  }

  /** Client credentials as Spring Security requests them, over the same timeouts. */
  private static ClientCredentialsOAuth2AuthorizedClientProvider tokens(
      ClientHttpRequestFactory http) {
    var tokenClient = new RestClientClientCredentialsTokenResponseClient();
    tokenClient.setRestClient(
        RestClient.builder()
            .requestFactory(http)
            .messageConverters(
                converters -> {
                  converters.clear();
                  converters.add(new FormHttpMessageConverter());
                  converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build());
    var provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
    provider.setAccessTokenResponseClient(tokenClient);
    return provider;
  }
}

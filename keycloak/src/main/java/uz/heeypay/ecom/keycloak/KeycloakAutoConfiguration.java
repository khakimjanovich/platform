package uz.heeypay.ecom.keycloak;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
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
 * it expires; a token Keycloak refuses (a restart, a rotated secret) is dropped and the call tried
 * once more with a new one. Every call, the token request included, gives up after the configured
 * timeouts, over Boot's HTTP client settings ({@code spring.http.clients.*}: SSL bundle,
 * redirects).
 */
@AutoConfiguration(
    after = {HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class})
@ConditionalOnProperty(prefix = KeycloakConstants.PROPERTIES_PREFIX, name = "url")
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  KeycloakAdmin keycloakAdmin(
      KeycloakProperties keycloak,
      ObjectProvider<RestClient.Builder> builders,
      ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
      ObjectProvider<HttpClientSettings> settings) {
    var http =
        factories
            .getIfAvailable(ClientHttpRequestFactoryBuilder::detect)
            .build(
                settings
                    .getIfAvailable(HttpClientSettings::defaults)
                    .withTimeouts(keycloak.connectTimeout(), keycloak.readTimeout()));
    var registration =
        ClientRegistration.withRegistrationId(KeycloakConstants.REGISTRATION_ID)
            .clientId(keycloak.clientId())
            .clientSecret(keycloak.clientSecret())
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(keycloak.url() + KeycloakConstants.TOKEN_PATH.formatted(keycloak.realm()))
            .build();
    var registrations = new InMemoryClientRegistrationRepository(registration);
    var clients = new InMemoryOAuth2AuthorizedClientService(registrations);
    var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
    manager.setAuthorizedClientProvider(tokens(http));
    var login = new OAuth2ClientHttpRequestInterceptor(manager);
    login.setClientRegistrationIdResolver(request -> KeycloakConstants.REGISTRATION_ID);
    login.setAuthorizationFailureHandler(
        OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(clients));
    return new KeycloakAdmin(
        builders
            .getIfAvailable(RestClient::builder)
            .baseUrl(keycloak.url() + KeycloakConstants.ADMIN_PATH.formatted(keycloak.realm()))
            .requestFactory(http)
            // Boot's logging first, then the retry, then the token: a retry logs in again.
            .requestInterceptor(new RetryOnceOnUnauthorized())
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

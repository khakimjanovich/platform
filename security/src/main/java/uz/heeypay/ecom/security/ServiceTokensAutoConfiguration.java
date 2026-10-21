package uz.heeypay.ecom.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Dormant until the app has OAuth2 client registrations (Spring Boot's {@code
 * spring.security.oauth2.client.registration.*}): then the HTTP service groups listed in {@code
 * ecom.security.service-tokens.groups} send client-credentials tokens ({@link ServiceTokens}). Boot
 * makes the registrations and the authorized-client store; this adds only the group wiring.
 *
 * <p>The tokens come from the app's own {@link OAuth2AuthorizedClientManager} bean when it has one.
 * Boot defines none, and Spring Security's default ({@link DefaultOAuth2AuthorizedClientManager})
 * needs a servlet request, which a scheduled job's call has not; so by default the manager is one
 * over the authorized-client store, its token requests going through Boot's {@link
 * RestClient.Builder} (logged, traced, the app's customizers) with their own timeouts.
 */
@AutoConfiguration(afterName = SecurityConstants.BOOT_OAUTH2_CLIENT)
@ConditionalOnClass(
    name = {
      SecurityConstants.CLIENT_REGISTRATIONS,
      SecurityConstants.SERVICE_GROUP_CONFIGURER,
      SecurityConstants.HTTP_CLIENT_FACTORY
    })
@ConditionalOnBean(ClientRegistrationRepository.class)
@EnableConfigurationProperties(ServiceTokensProperties.class)
public class ServiceTokensAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  ServiceTokens serviceTokens(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientService clients,
      ServiceTokensProperties properties,
      ObjectProvider<OAuth2AuthorizedClientManager> managers,
      ObjectProvider<RestClient.Builder> builders,
      ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
      ObjectProvider<HttpClientSettings> settings) {
    var manager =
        managers
            .orderedStream()
            .filter(own -> !(own instanceof DefaultOAuth2AuthorizedClientManager))
            .findFirst()
            .orElseGet(
                () -> {
                  var store =
                      new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                          registrations, clients);
                  store.setAuthorizedClientProvider(
                      tokens(builders, factories, settings, properties));
                  return store;
                });
    return new ServiceTokens(manager, clients, properties.groups());
  }

  /** Client credentials as Spring Security requests them, over Boot's client and our timeouts. */
  private static ClientCredentialsOAuth2AuthorizedClientProvider tokens(
      ObjectProvider<RestClient.Builder> builders,
      ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
      ObjectProvider<HttpClientSettings> settings,
      ServiceTokensProperties properties) {
    var http =
        factories
            .getIfAvailable(ClientHttpRequestFactoryBuilder::detect)
            .build(
                settings
                    .getIfAvailable(HttpClientSettings::defaults)
                    .withTimeouts(properties.connectTimeout(), properties.readTimeout()));
    var tokenClient = new RestClientClientCredentialsTokenResponseClient();
    tokenClient.setRestClient(
        builders
            .getIfAvailable(RestClient::builder)
            .requestFactory(http)
            .configureMessageConverters(
                converters ->
                    converters
                        .disableDefaults()
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build());
    var provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
    provider.setAccessTokenResponseClient(tokenClient);
    return provider;
  }
}

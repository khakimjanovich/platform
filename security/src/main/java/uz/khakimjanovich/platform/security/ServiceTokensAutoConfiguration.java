package uz.khakimjanovich.platform.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.ssl.SslBundles;
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
      ObjectProvider<HttpClientSettings> settings,
      ObjectProvider<SslBundles> bundles) {
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
                      tokens(builders, factories, settings, bundles, properties));
                  return store;
                });
    return new ServiceTokens(manager, clients, properties.groups());
  }

  private static ClientCredentialsOAuth2AuthorizedClientProvider tokens(
      ObjectProvider<RestClient.Builder> builders,
      ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
      ObjectProvider<HttpClientSettings> settings,
      ObjectProvider<SslBundles> bundles,
      ServiceTokensProperties properties) {
    var timed =
        settings
            .getIfAvailable(HttpClientSettings::defaults)
            .withTimeouts(properties.connectTimeout(), properties.readTimeout());
    // A bundle not defined stops startup here (NoSuchSslBundleException).
    var http =
        factories
            .getIfAvailable(ClientHttpRequestFactoryBuilder::detect)
            .build(
                properties.sslBundle() == null
                    ? timed
                    : timed.withSslBundle(bundles.getObject().getBundle(properties.sslBundle())));
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

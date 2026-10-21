package uz.heeypay.ecom.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Dormant until the app has OAuth2 client registrations (Spring Boot's {@code
 * spring.security.oauth2.client.registration.*}): then the HTTP service groups listed in {@code
 * ecom.security.service-tokens.groups} send client-credentials tokens ({@link ServiceTokens}). Boot
 * makes the registrations and the authorized-client store; this adds only the group wiring.
 */
@AutoConfiguration(afterName = SecurityConstants.BOOT_OAUTH2_CLIENT)
@ConditionalOnClass(
    name = {SecurityConstants.CLIENT_REGISTRATIONS, SecurityConstants.SERVICE_GROUP_CONFIGURER})
@ConditionalOnBean(ClientRegistrationRepository.class)
@EnableConfigurationProperties(ServiceTokensProperties.class)
public class ServiceTokensAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  ServiceTokens serviceTokens(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientService clients,
      ServiceTokensProperties properties) {
    return new ServiceTokens(
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients),
        clients,
        properties.groups());
  }
}

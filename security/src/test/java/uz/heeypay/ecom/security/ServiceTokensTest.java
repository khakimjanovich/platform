package uz.heeypay.ecom.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

/** Service tokens wait for a registration, arm with one, and give way to the app's own. */
class ServiceTokensTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OAuth2ClientAutoConfiguration.class, ServiceTokensAutoConfiguration.class));

  private static final String[] REGISTRATION = {
    "spring.security.oauth2.client.registration.adapters.client-id=ecom-app",
    "spring.security.oauth2.client.registration.adapters.client-secret=local-only",
    "spring.security.oauth2.client.registration.adapters.authorization-grant-type=client_credentials",
    "spring.security.oauth2.client.registration.adapters.provider=keycloak",
    "spring.security.oauth2.client.provider.keycloak.token-uri=http://localhost:8180/realms/ecom/protocol/openid-connect/token",
    "ecom.security.service-tokens.groups.uzcard=adapters"
  };

  @Test
  void dormantWithoutARegistration() {
    runner.run(context -> assertThat(context).doesNotHaveBean(ServiceTokens.class));
  }

  @Test
  void armedWithOne() {
    runner
        .withPropertyValues(REGISTRATION)
        .run(context -> assertThat(context).hasSingleBean(ServiceTokens.class));
  }

  @Test
  void theAppsOwnBeanWins() {
    var own =
        new ServiceTokens(
            mock(OAuth2AuthorizedClientManager.class),
            mock(OAuth2AuthorizedClientService.class),
            Map.of());
    runner
        .withPropertyValues(REGISTRATION)
        .withBean(ServiceTokens.class, () -> own)
        .run(context -> assertThat(context.getBean(ServiceTokens.class)).isSameAs(own));
  }
}

package uz.heeypay.ecom.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Service tokens wait for a registration, arm with one, give way to the app's own bean and manager,
 * and retry only a refused token.
 */
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

  @Test
  void theAppsOwnManagerGetsTheTokens() {
    var manager = mock(OAuth2AuthorizedClientManager.class);
    var registration =
        ClientRegistration.withRegistrationId("adapters")
            .clientId("ecom-app")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("http://keycloak/token")
            .build();
    var token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "t1", Instant.now(), Instant.now().plusSeconds(60));
    when(manager.authorize(any()))
        .thenReturn(new OAuth2AuthorizedClient(registration, "service-tokens", token));
    runner
        .withPropertyValues(REGISTRATION)
        .withBean(OAuth2AuthorizedClientManager.class, () -> manager)
        .run(
            context -> {
              var builder =
                  RestClient.builder()
                      .requestInterceptor(
                          context.getBean(ServiceTokens.class).interceptor("adapters"));
              var server = MockRestServiceServer.bindTo(builder).build();
              server
                  .expect(requestTo("/ping"))
                  .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer t1"))
                  .andRespond(withSuccess());
              builder.build().get().uri("/ping").retrieve().toBodilessEntity();
              verify(manager).authorize(any());
            });
  }

  @Test
  void onlyARefusedTokenIsTriedAgain() {
    var builder =
        RestClient.builder().requestInterceptor(new ServiceTokens.RetryOnceOnUnauthorized());
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(ExpectedCount.once(), requestTo("/scope"))
        .andRespond(
            withStatus(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\""));
    var client = builder.build();
    int status = client.get().uri("/scope").exchange((req, res) -> res.getStatusCode().value());
    assertThat(status).isEqualTo(401);
    server.verify();
  }
}

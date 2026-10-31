package uz.khakimjanovich.platform.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

/** Trusted issuers that would accept or refuse silently stop startup instead. */
class TrustedIssuersTest {

  private static final String URI = "https://id.example/realms/a";

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class App {}

  @Test
  void anIssuerWithoutItsAudienceClientsOrRolesStopsStartup() {
    assertThatThrownBy(
            () -> properties(new SecurityProperties.Issuer(URI, "api", List.of(), List.of("r"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(URI);
    assertThatThrownBy(
            () -> properties(new SecurityProperties.Issuer(URI, " ", List.of("c"), List.of("r"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> properties(new SecurityProperties.Issuer(URI, "api", List.of("c"), List.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> start("platform.security.issuers[0].uri=" + URI))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .rootCause()
        .hasMessageContaining(URI);
  }

  @Test
  void anIssuerHasEitherClientsOrAClientGroup() {
    properties(new SecurityProperties.Issuer(URI, "api", null, "callers", List.of("r")));
    assertThatThrownBy(
            () ->
                properties(
                    new SecurityProperties.Issuer(
                        URI, "api", List.of("c"), "callers", List.of("r"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> properties(new SecurityProperties.Issuer(URI, "api", null, " ", List.of("r"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aClientGroupWithoutClientGroupsStopsStartup() {
    assertThatThrownBy(
            () ->
                start(
                    "platform.security.issuers[0].uri=" + URI,
                    "platform.security.issuers[0].audience=api",
                    "platform.security.issuers[0].client-group=callers",
                    "platform.security.issuers[0].roles=r"))
        .rootCause()
        .hasMessage(SecurityConstants.CLIENT_GROUPS_MISSING.formatted(URI, "callers"));
  }

  @Test
  void oneIssuerMayBeListedForSeveralClients() {
    properties(
        new SecurityProperties.Issuer(URI, "api", List.of("a"), List.of("r")),
        new SecurityProperties.Issuer(URI, "api", List.of("b"), List.of("s")));
  }

  @Test
  void aClientOfAnIssuerListedTwiceStopsStartup() {
    assertThatThrownBy(
            () ->
                properties(
                    new SecurityProperties.Issuer(URI, "api", List.of("a"), List.of("r")),
                    new SecurityProperties.Issuer(URI, "api", List.of("b", "a"), List.of("s"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SecurityConstants.ISSUER_CLIENT_TWICE.formatted("a", URI));
  }

  @Test
  void anIssuerWithTwoAudiencesStopsStartup() {
    assertThatThrownBy(
            () ->
                properties(
                    new SecurityProperties.Issuer(URI, "api", List.of("a"), List.of("r")),
                    new SecurityProperties.Issuer(URI, "other", List.of("b"), List.of("s"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SecurityConstants.ISSUER_AUDIENCES.formatted(URI));
  }

  @Test
  void issuersAndBootsIssuerUriTogetherStopStartup() {
    assertThatThrownBy(
            () ->
                start(
                    "platform.security.issuers[0].uri=" + URI,
                    "platform.security.issuers[0].audience=api",
                    "platform.security.issuers[0].clients=c",
                    "platform.security.issuers[0].roles=r",
                    SecurityConstants.BOOT_ISSUER_URI + "=" + URI))
        .rootCause()
        .hasMessage(SecurityConstants.ISSUERS_AND_ISSUER_URI);
  }

  private static SecurityProperties properties(SecurityProperties.Issuer... issuers) {
    return new SecurityProperties(
        List.of(), List.of(), new LinkedHashMap<>(), List.of(issuers), null);
  }

  private static void start(String... properties) {
    new SpringApplicationBuilder(App.class)
        .web(WebApplicationType.SERVLET)
        .properties("server.port=0")
        .properties(properties)
        .run()
        .close();
  }
}

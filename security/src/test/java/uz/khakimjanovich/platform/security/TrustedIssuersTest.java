package uz.khakimjanovich.platform.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

class TrustedIssuersTest {

  private static final String URI = "https://id.example/realms/a";

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class App {}

  @Test
  void anIssuerWithoutItsUriAudienceOrRolesStopsStartup() {
    assertThatThrownBy(() -> properties(new SecurityProperties.Issuer(" ", "api", List.of("r"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(new SecurityProperties.Issuer(URI, " ", List.of("r"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SecurityConstants.ISSUER_INCOMPLETE.formatted(URI));
    assertThatThrownBy(() -> properties(new SecurityProperties.Issuer(URI, "api", List.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> start("platform.security.issuers[0].uri=" + URI))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .rootCause()
        .hasMessageContaining(URI);
  }

  @Test
  void anIssuerListedTwiceStopsStartup() {
    assertThatThrownBy(
            () ->
                properties(
                    new SecurityProperties.Issuer(URI, "api", List.of("r")),
                    new SecurityProperties.Issuer(URI, "api", List.of("s"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SecurityConstants.ISSUER_TWICE.formatted(URI));
  }

  @Test
  void issuersAndBootsIssuerUriTogetherStopStartup() {
    assertThatThrownBy(
            () ->
                start(
                    "platform.security.issuers[0].uri=" + URI,
                    "platform.security.issuers[0].audience=api",
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

package uz.khakimjanovich.platform.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

@Testcontainers
class VaultConfigIT {

  @Container
  static final VaultContainer<?> VAULT =
      new VaultContainer<>(ResourceBundle.getBundle("images").getString("vault"))
          .withVaultToken("test")
          .withInitCommand(
              "kv put secret/test"
                  + " platform.example.key=bG9jYWwtb25seS1rZXktZm9yLWNhcmQtZGF0YS1rMSE="
                  + " platform.otp.secret=from-vault");

  @Configuration(proxyBeanMethods = false)
  static class App {}

  private static SpringApplicationBuilder app(String importLocation) {
    return new SpringApplicationBuilder(App.class)
        .web(WebApplicationType.NONE)
        .properties(
            "spring.config.import=" + importLocation,
            "spring.cloud.vault.uri=" + VAULT.getHttpHostAddress(),
            "spring.cloud.vault.token=test",
            "spring.cloud.vault.fail-fast=true");
  }

  @Test
  void secretsBindLikeAnyProperty() {
    try (var context = app("vault://secret/test").run()) {
      var environment = context.getEnvironment();
      assertThat(environment.getProperty("platform.example.key"))
          .isEqualTo("bG9jYWwtb25seS1rZXktZm9yLWNhcmQtZGF0YS1rMSE=");
      assertThat(environment.getProperty("platform.otp.secret")).isEqualTo("from-vault");
    }
  }

  @Test
  void aMissingPathLeavesItsPropertiesUnset() {
    try (var context = app("vault://secret/missing").run()) {
      assertThat(context.getEnvironment().getProperty("platform.otp.secret")).isNull();
    }
  }

  @Test
  void anUnreachableVaultStopsStartup() {
    assertThatThrownBy(
            () ->
                app("vault://secret/test")
                    .properties("spring.cloud.vault.uri=http://localhost:1")
                    .run())
        .isNotNull();
  }

  @Test
  void anUnreachableVaultIsSkippedWhenOptionalAndNotFailFast() {
    try (var context =
        app("optional:vault://secret/test")
            .properties(
                "spring.cloud.vault.uri=http://localhost:1", "spring.cloud.vault.fail-fast=false")
            .run()) {
      assertThat(context.getEnvironment().getProperty("platform.otp.secret")).isNull();
    }
  }
}

package uz.heeypay.ecom.vault;

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

/**
 * Vault as a configuration source: an app imports vault://secret/<name> and its secrets bind like
 * any property. With fail-fast, a Vault that can't be read stops startup unless the import is
 * optional.
 */
@Testcontainers
class VaultConfigIT {

  @Container
  static final VaultContainer<?> VAULT =
      new VaultContainer<>(ResourceBundle.getBundle("images").getString("vault"))
          .withVaultToken("test")
          .withSecretInVault(
              "secret/test",
              "ecom.crypto.keys.k1=bG9jYWwtb25seS1rZXktZm9yLWNhcmQtZGF0YS1rMSE=",
              "ecom.otp.secret=from-vault");

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
      assertThat(environment.getProperty("ecom.crypto.keys.k1"))
          .isEqualTo("bG9jYWwtb25seS1rZXktZm9yLWNhcmQtZGF0YS1rMSE=");
      assertThat(environment.getProperty("ecom.otp.secret")).isEqualTo("from-vault");
    }
  }

  /** Spring Cloud Vault reads a missing path as no secrets: the library needing one refuses. */
  @Test
  void aMissingPathLeavesItsPropertiesUnset() {
    try (var context = app("vault://secret/missing").run()) {
      assertThat(context.getEnvironment().getProperty("ecom.otp.secret")).isNull();
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

  /** As locally: no fail-fast, an optional import, and the app starts without Vault. */
  @Test
  void anUnreachableVaultIsSkippedWhenOptionalAndNotFailFast() {
    try (var context =
        app("optional:vault://secret/test")
            .properties(
                "spring.cloud.vault.uri=http://localhost:1", "spring.cloud.vault.fail-fast=false")
            .run()) {
      assertThat(context.getEnvironment().getProperty("ecom.otp.secret")).isNull();
    }
  }
}

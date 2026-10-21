package uz.heeypay.ecom.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

/**
 * Card-data encryption with its keys from a real Vault, as the app gets them: two keys, the active
 * one sealing; after the bank flips the active key in Vault and the app restarts, old values still
 * read and rewrap moves them to the new key.
 */
@Testcontainers
class CryptoIT {

  private static final String PAN = "8600301234567890";
  private static final String K1 = key();
  private static final String K2 = key();

  @Container
  static final VaultContainer<?> VAULT =
      new VaultContainer<>(ResourceBundle.getBundle("images").getString("vault"))
          .withVaultToken("test");

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(CryptoAutoConfiguration.class)
  static class App {}

  /** As the bank does it: the whole secret at once. */
  private static void putKeys(String active) throws Exception {
    var put =
        VAULT.execInContainer(
            "vault",
            "kv",
            "put",
            "secret/crypto-it",
            "ecom.crypto.active-key-id=" + active,
            "ecom.crypto.keys.k1=" + K1,
            "ecom.crypto.keys.k2=" + K2,
            "ecom.crypto.blind-index-key=" + K1);
    assertThat(put.getExitCode()).as(put.getStderr()).isZero();
  }

  private static ConfigurableApplicationContext app() {
    return new SpringApplicationBuilder(App.class)
        .web(WebApplicationType.NONE)
        .properties(
            "spring.config.import=vault://secret/crypto-it",
            "spring.cloud.vault.uri=" + VAULT.getHttpHostAddress(),
            "spring.cloud.vault.token=test",
            "spring.cloud.vault.fail-fast=true")
        .run();
  }

  @Test
  void keysFromVaultSealAndRotate() throws Exception {
    putKeys("k1");
    String sealed;
    try (var context = app()) {
      var crypto = context.getBean(Crypto.class);
      assertThat(crypto.keyIds()).containsExactlyInAnyOrder("k1", "k2");
      sealed = crypto.encrypt(PAN);
      assertThat(sealed).startsWith("v1:k1:");
    }

    putKeys("k2");
    try (var context = app()) {
      var crypto = context.getBean(Crypto.class);
      assertThat(crypto.activeKeyId()).isEqualTo("k2");
      assertThat(crypto.decrypt(sealed)).as("old values still read").isEqualTo(PAN);
      assertThat(crypto.isStale(sealed)).isTrue();
      var rewrapped = crypto.rewrap(sealed);
      assertThat(rewrapped).startsWith("v1:k2:");
      assertThat(crypto.decrypt(rewrapped)).isEqualTo(PAN);
    }
  }

  private static String key() {
    var bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}

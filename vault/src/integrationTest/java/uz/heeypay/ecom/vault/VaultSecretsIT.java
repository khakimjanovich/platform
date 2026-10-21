package uz.heeypay.ecom.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Secrets against a real Vault. */
@Testcontainers
class VaultSecretsIT {

  @Container
  static final GenericContainer<?> VAULT =
      new GenericContainer<>("hashicorp/vault:1.20.4")
          .withCommand("server", "-dev")
          .withEnv("VAULT_DEV_ROOT_TOKEN_ID", "test")
          .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
          .withExposedPorts(8200)
          .waitingFor(Wait.forHttp("/v1/sys/health").forPort(8200));

  @Test
  void theLatestVersionOfASecretIsRead() {
    var secrets = secrets("test");
    assertThat(secrets.get("merchants/none")).isEmpty();
    put("merchants/shop-1", "one");
    put("merchants/shop-1", "two");
    assertThat(secrets.get("merchants/shop-1")).contains(Map.of("signingKey", "two"));
  }

  /** As the bank does it: straight into Vault, e.g. {@code vault kv put}. */
  private static void put(String path, String key) {
    RestClient.create(url())
        .post()
        .uri("/v1/secret/data/" + path)
        .header("X-Vault-Token", "test")
        .body(Map.of("data", Map.of("signingKey", key)))
        .retrieve()
        .toBodilessEntity();
  }

  @Test
  void aWrongTokenFailsClosed() {
    assertThatThrownBy(() -> secrets("wrong").get("merchants/shop-1"))
        .isInstanceOf(VaultUnavailableException.class);
  }

  private static VaultSecrets secrets(String token) {
    return new VaultAutoConfiguration()
        .vaultSecrets(
            new VaultProperties(url(), token, "secret"),
            new StaticListableBeanFactory().getBeanProvider(RestClient.Builder.class));
  }

  private static String url() {
    return "http://" + VAULT.getHost() + ":" + VAULT.getMappedPort(8200);
  }
}

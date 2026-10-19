package uz.heeypay.ecom.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
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
  void aSecretIsWrittenReplacedAndRead() {
    var secrets = secrets("test");
    assertThat(secrets.get("merchants/none")).isEmpty();
    secrets.put("merchants/shop-1", Map.of("signingKey", "one"));
    secrets.put("merchants/shop-1", Map.of("signingKey", "two"));
    assertThat(secrets.get("merchants/shop-1")).contains(Map.of("signingKey", "two"));
  }

  @Test
  void aWrongTokenFailsClosed() {
    assertThatThrownBy(() -> secrets("wrong").get("merchants/shop-1"))
        .isInstanceOf(VaultUnavailableException.class);
  }

  private static VaultSecrets secrets(String token) {
    var url = "http://" + VAULT.getHost() + ":" + VAULT.getMappedPort(8200);
    return new VaultAutoConfiguration().vaultSecrets(new VaultProperties(url, token, "secret"));
  }
}

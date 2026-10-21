package uz.heeypay.ecom.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

/** Secrets against a real Vault, signed in as the app would be: with a token or with AppRole. */
@Testcontainers
class VaultSecretsIT {

  @Container
  static final VaultContainer<?> VAULT =
      new VaultContainer<>(ResourceBundle.getBundle("images").getString("vault"))
          .withVaultToken("test")
          .withInitCommand("auth enable approle");

  private static ApplicationContextRunner app(String... properties) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                org.springframework.cloud.vault.config.VaultAutoConfiguration.class,
                VaultAutoConfiguration.class))
        .withPropertyValues("spring.cloud.vault.uri=" + VAULT.getHttpHostAddress())
        .withPropertyValues(properties);
  }

  private static ApplicationContextRunner withToken(String token) {
    return app("spring.cloud.vault.authentication=TOKEN", "spring.cloud.vault.token=" + token);
  }

  /** As the bank does it: straight into Vault, e.g. {@code vault kv put}. */
  private static void put(String path, String key) throws Exception {
    var result = VAULT.execInContainer("vault", "kv", "put", "secret/" + path, "signingKey=" + key);
    assertThat(result.getExitCode()).as(result.getStderr()).isZero();
  }

  @Test
  void aTokenReadsTheLatestVersionAndAMissingPathIsEmpty() throws Exception {
    put("merchants/shop-1", "one");
    put("merchants/shop-1", "two");
    withToken("test")
        .run(
            context -> {
              var secrets = context.getBean(VaultSecrets.class);
              assertThat(secrets.get("merchants/shop-1")).contains(Map.of("signingKey", "two"));
              assertThat(secrets.get("merchants/none")).isEmpty();
            });
  }

  @Test
  void appRoleSignsIn() throws Exception {
    put("merchants/shop-2", "approle");
    // The app's role may only read secrets, as in a real Vault.
    var policy =
        VAULT.execInContainer(
            "sh",
            "-c",
            "echo 'path \"secret/data/*\" { capabilities = [\"read\"] }'"
                + " | vault policy write read-secrets -");
    assertThat(policy.getExitCode()).as(policy.getStderr()).isZero();
    vault("write", "auth/approle/role/ecom", "token_policies=read-secrets");
    var roleId = read("auth/approle/role/ecom/role-id", "role_id");
    var secretId = write("auth/approle/role/ecom/secret-id", "secret_id");
    app(
            "spring.cloud.vault.authentication=APPROLE",
            "spring.cloud.vault.app-role.role-id=" + roleId,
            "spring.cloud.vault.app-role.secret-id=" + secretId)
        .run(
            context ->
                assertThat(context.getBean(VaultSecrets.class).get("merchants/shop-2"))
                    .contains(Map.of("signingKey", "approle")));
  }

  @Test
  void aWrongTokenFailsClosed() {
    withToken("wrong")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(VaultSecrets.class).get("merchants/x"))
                    .isInstanceOf(VaultUnavailableException.class));
  }

  @Test
  void aKeyRotatedInVaultIsSeenAfterTheCacheTtl() throws Exception {
    put("merchants/shop-3", encode("first"));
    withToken("test")
        .withPropertyValues("ecom.vault.signing-keys.cache-ttl=1s")
        .run(
            context -> {
              var keys = context.getBean(SigningKeys.class);
              assertThat(keys.find("shop-3")).contains(bytes("first"));
              put("merchants/shop-3", encode("second"));
              assertThat(keys.find("shop-3")).as("within the TTL").contains(bytes("first"));
              Thread.sleep(Duration.ofMillis(1_200));
              assertThat(keys.find("shop-3")).contains(bytes("second"));
            });
  }

  private static String read(String path, String field) throws Exception {
    return vault("read", "-field=" + field, path);
  }

  private static String write(String path, String field) throws Exception {
    return vault("write", "-f", "-field=" + field, path);
  }

  private static String vault(String... command) throws Exception {
    var full = new String[command.length + 1];
    full[0] = "vault";
    System.arraycopy(command, 0, full, 1, command.length);
    var result = VAULT.execInContainer(full);
    assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    return result.getStdout().strip();
  }

  private static String encode(String key) {
    return Base64.getEncoder().encodeToString(bytes(key));
  }

  private static byte[] bytes(String key) {
    return key.getBytes(StandardCharsets.UTF_8);
  }
}

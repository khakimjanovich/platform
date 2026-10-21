package uz.heeypay.ecom.vault;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * What the library reads from Vault. Where Vault is and how the app signs in are Spring Cloud
 * Vault's own {@code spring.cloud.vault.*} (TOKEN locally, APPROLE or KUBERNETES elsewhere).
 *
 * @param mount the KV version 2 secrets engine's mount
 * @param signingKeys merchants' request-signing keys
 */
@Validated
@ConfigurationProperties(VaultConstants.PROPERTIES_PREFIX)
public record VaultProperties(
    @DefaultValue(VaultConstants.DEFAULT_MOUNT) String mount,
    @DefaultValue @Valid SigningKeysProperties signingKeys) {

  /**
   * Where the signing keys are and how long one read is kept.
   *
   * @param path the path of a client's key, {@code %s} being its client id (required: without it
   *     every client would verify with one key)
   * @param cacheTtl how long a key read from Vault is used before reading it again
   */
  public record SigningKeysProperties(
      @DefaultValue(VaultConstants.SIGNING_KEY_PATH)
          @Pattern(regexp = ".*%s.*", message = "must contain %s, the client id")
          String path,
      @DefaultValue(VaultConstants.DEFAULT_CACHE_TTL) Duration cacheTtl) {}
}

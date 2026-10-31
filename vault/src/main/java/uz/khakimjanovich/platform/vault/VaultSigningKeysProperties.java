package uz.khakimjanovich.platform.vault;

import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Signing keys ({@link SigningKeys}), for an app whose API clients sign their requests. Where the
 * keys are is the app's own: none of these has a default. Without {@code path} there are no signing
 * keys, and with it {@code field} and {@code cache-ttl} must be set too.
 *
 * @param path the KV entry of a client's key, under {@code platform.vault.mount}; {@code %s} is its
 *     client id (required in it: without it every client would verify with one key)
 * @param field the entry's key holding the base64 signing key
 * @param cacheTtl how long a key read from Vault is used before reading it again
 */
@Validated
@ConfigurationProperties(VaultConstants.SIGNING_KEYS_PREFIX)
public record VaultSigningKeysProperties(
    @Nullable @Pattern(regexp = ".*%s.*", message = "must contain %s, the client id") String path,
    @Nullable String field,
    @Nullable Duration cacheTtl) {}

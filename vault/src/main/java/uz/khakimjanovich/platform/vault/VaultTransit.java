package uz.khakimjanovich.platform.vault;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.VaultHmacRequest;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import uz.khakimjanovich.platform.error.GeneralException;

/**
 * Values sealed with Vault Transit: the keys never leave Vault. The module names the key and gives
 * a context that says where the value lives (for example {@code <table>.<column>:<row id>}); with a
 * derived key ({@code derived=true}) a ciphertext copied to another row or column does not open.
 * Every failure fails closed with a {@link GeneralException}: {@code VAULT_TRANSIT_UNAVAILABLE}
 * when Vault didn't answer, failed or refused the app's token (a later call may work), {@code
 * VAULT_TRANSIT_REFUSED} when Vault answered that this call can't work (a ciphertext that doesn't
 * open with its context, a tampered one, a key that doesn't exist).
 */
@Slf4j
public class VaultTransit {

  private final VaultTransitOperations transit;

  public VaultTransit(VaultOperations vault, String mount) {
    this.transit = vault.opsForTransit(mount);
  }

  /** The value sealed with {@code key}'s newest version and bound to {@code context}. */
  public String encrypt(String key, String context, String plain) {
    return vault(
        () ->
            transit
                .encrypt(
                    key,
                    Plaintext.of(plain.getBytes(StandardCharsets.UTF_8)).with(context(context)))
                .getCiphertext());
  }

  /** The value {@link #encrypt} sealed with the same key and context. */
  public String decrypt(String key, String context, String sealed) {
    return vault(
        () -> transit.decrypt(key, Ciphertext.of(sealed).with(context(context))).asString());
  }

  /**
   * The value sealed again under {@code key}'s newest version, its context unchanged; the plaintext
   * never leaves Vault. A module's rewrap job moves every value that does not start with {@link
   * #latestPrefix} after a key rotation; the operator may then retire the old versions.
   */
  public String rewrap(String key, String context, String sealed) {
    return vault(() -> transit.rewrap(key, sealed, context(context)));
  }

  /** {@code vault:v<newest version>:}, the start of every value sealed with the newest version. */
  public String latestPrefix(String key) {
    var found = vault(() -> transit.getKey(key));
    if (found == null) {
      log.warn(VaultConstants.TRANSIT_KEY_MISSING, key);
      throw new GeneralException(VaultExceptionStatus.VAULT_TRANSIT_REFUSED);
    }
    return VaultConstants.TRANSIT_PREFIX_FORMAT.formatted(found.getLatestVersion());
  }

  /**
   * A keyed hash (blind index) of {@code value} with {@code key} at {@code keyVersion}: the same
   * value always gives the same hash, so a unique column can hold it. The module pins the version;
   * a rotation would change every hash.
   */
  public String hmac(String key, int keyVersion, String value) {
    return vault(
        () ->
            transit
                .getHmac(
                    key,
                    VaultHmacRequest.builder()
                        .plaintext(Plaintext.of(value.getBytes(StandardCharsets.UTF_8)))
                        .keyVersion(keyVersion)
                        .build())
                .getHmac());
  }

  private static VaultTransitContext context(String context) {
    return VaultTransitContext.builder().context(context.getBytes(StandardCharsets.UTF_8)).build();
  }

  private static <T extends @Nullable Object> T vault(Supplier<T> call) {
    try {
      return call.get();
    } catch (VaultException | RestClientException e) { // Vault refused, or didn't answer at all
      // Vault's answer names the status and the path, never a value.
      log.warn(VaultConstants.TRANSIT_FAILED, e.getClass().getSimpleName(), e.getMessage());
      throw new GeneralException(
          refused(e)
              ? VaultExceptionStatus.VAULT_TRANSIT_REFUSED
              : VaultExceptionStatus.VAULT_TRANSIT_UNAVAILABLE);
    }
  }

  /**
   * Vault answers 400 to a ciphertext that doesn't open (wrong context, tampered, unknown key) and
   * 404 to a missing key: the call can't work. Anything else (no answer, 5xx, a refused token) may
   * pass later.
   */
  private static boolean refused(Exception e) {
    var cause = e instanceof HttpClientErrorException ? e : e.getCause();
    return cause instanceof HttpClientErrorException client
        && VaultConstants.TRANSIT_REFUSED_STATUSES.contains(client.getStatusCode().value());
  }
}

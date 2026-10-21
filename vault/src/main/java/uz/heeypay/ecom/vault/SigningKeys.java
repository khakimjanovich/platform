package uz.heeypay.ecom.vault;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Each merchant API client's request-signing key, put into Vault by the bank under the configured
 * path (by default {@code merchants/<client id>}, e.g. {@code vault kv put secret/merchants/<client
 * id> signingKey=<base64>}). The app never creates or shows one. Every module that checks or makes
 * a signature reads it here, so the key never travels between modules.
 *
 * <p>A key read is kept for a short time, so a burst of signed requests reads Vault once per
 * client. A client without a key is never remembered (the bank may add one any moment), and a Vault
 * failure never falls back to an old key.
 */
public class SigningKeys {

  /** A client id as Keycloak makes them: never a path of its own ({@code ..}, {@code /}). */
  private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*");

  private final VaultSecrets vault;
  private final String path;
  private final Duration ttl;
  private final Clock clock;
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  private record Cached(byte[] key, Instant until) {}

  public SigningKeys(VaultSecrets vault, String path, Duration ttl, Clock clock) {
    this.vault = vault;
    this.path = path;
    this.ttl = ttl;
    this.clock = clock;
  }

  /**
   * The client's key, or empty if the bank hasn't set one or the id can't be a client's. Throws
   * {@link VaultUnavailableException} if Vault can't be reached, so callers fail closed.
   */
  public Optional<byte[]> find(String clientId) {
    if (!CLIENT_ID.matcher(clientId).matches()) return Optional.empty();
    var now = clock.instant();
    var cached = cache.get(clientId);
    if (cached != null && now.isBefore(cached.until())) return Optional.of(cached.key().clone());
    cache.remove(clientId);
    var key =
        vault
            .get(path.formatted(clientId))
            .map(secret -> secret.get(VaultConstants.SIGNING_KEY))
            .map(Base64.getDecoder()::decode);
    key.ifPresent(bytes -> cache.put(clientId, new Cached(bytes.clone(), now.plus(ttl))));
    return key;
  }
}

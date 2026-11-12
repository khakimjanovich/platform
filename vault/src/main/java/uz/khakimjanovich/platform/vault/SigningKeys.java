package uz.khakimjanovich.platform.vault;

import static uz.khakimjanovich.platform.vault.VaultConstants.CLIENT_ID;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SigningKeys {

  private final VaultSecrets vault;
  private final String path;
  private final String field;
  private final Duration ttl;
  private final Clock clock;
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  private static final class Cached {
    private final byte[] key;
    private final Instant until;

    private Cached(byte[] key, Instant until) {
      this.key = key;
      this.until = until;
    }
  }

  public SigningKeys(VaultSecrets vault, String path, String field, Duration ttl, Clock clock) {
    this.vault = vault;
    this.path = path;
    this.field = field;
    this.ttl = ttl;
    this.clock = clock;
  }

  public Optional<byte[]> find(String clientId) {
    if (!CLIENT_ID.matcher(clientId).matches()) return Optional.empty();
    var now = clock.instant();
    var cached = cache.get(clientId);
    if (cached != null && now.isBefore(cached.until)) return Optional.of(cached.key.clone());
    cache.remove(clientId);
    var key =
        vault
            .get(path.formatted(clientId))
            .map(secret -> secret.get(field))
            .map(Base64.getDecoder()::decode);
    key.ifPresent(bytes -> cache.put(clientId, new Cached(bytes.clone(), now.plus(ttl))));
    return key;
  }
}

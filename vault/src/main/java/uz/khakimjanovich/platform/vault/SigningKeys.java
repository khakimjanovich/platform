package uz.khakimjanovich.platform.vault;

import static uz.khakimjanovich.platform.vault.VaultConstants.CLIENT_ID;
import static uz.khakimjanovich.platform.vault.VaultConstants.DEFAULT_SIGNING_KEYS_MAX_ENTRIES;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class SigningKeys {

  private final VaultSecrets vault;
  private final String path;
  private final String field;
  private final Duration ttl;
  private final Clock clock;
  private final int maxEntries;
  private final Map<String, Cached> cache;

  private static final class Cached {
    private final byte[] key;
    private final Instant until;

    private Cached(byte[] key, Instant until) {
      this.key = key;
      this.until = until;
    }
  }

  public SigningKeys(VaultSecrets vault, String path, String field, Duration ttl, Clock clock) {
    this(vault, path, field, ttl, clock, DEFAULT_SIGNING_KEYS_MAX_ENTRIES);
  }

  public SigningKeys(
      VaultSecrets vault, String path, String field, Duration ttl, Clock clock, int maxEntries) {
    if (maxEntries < 1) {
      throw new IllegalArgumentException("maxEntries must be at least 1, not " + maxEntries);
    }
    this.vault = Objects.requireNonNull(vault, "vault must not be null");
    this.path = Objects.requireNonNull(path, "path must not be null");
    this.field = Objects.requireNonNull(field, "field must not be null");
    this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.maxEntries = maxEntries;
    this.cache =
        Collections.synchronizedMap(
            new LinkedHashMap<String, Cached>(16, 0.75f, true) {
              @Override
              protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                return size() > SigningKeys.this.maxEntries;
              }
            });
  }

  public Optional<byte[]> find(String clientId) {
    if (!CLIENT_ID.matcher(clientId).matches()) return Optional.empty();
    var now = clock.instant();
    var cached = cache.get(clientId);
    if (cached != null) {
      if (now.isBefore(cached.until)) {
        return Optional.of(cached.key.clone());
      }
      cache.remove(clientId);
    }
    var key =
        vault
            .get(path.formatted(clientId))
            .map(secret -> secret.get(field))
            .map(Base64.getDecoder()::decode);
    key.ifPresent(bytes -> cache.put(clientId, new Cached(bytes.clone(), now.plus(ttl))));
    return key;
  }

  public int cacheSize() {
    return cache.size();
  }

  public int maxEntries() {
    return maxEntries;
  }
}

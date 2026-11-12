package uz.khakimjanovich.platform.valkey;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.connection.ExpirationOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Key names and counters in Valkey. Every key is {@code <service>:<kind>:<id>}: the service is the
 * app's (its ACL user reaches only {@code <service>:*}), the kind and the id are the module's. A
 * counter lives for its window from its first count, then starts again from zero.
 *
 * <p>The commands are only GET, DEL, INCR, EXPIRE and TTL, so the ACL user needs no script. An INCR
 * whose EXPIRE was lost (the app stopped between the two) gets its expiry on the next count. A
 * Valkey that can't be reached throws Spring's {@code DataAccessException}: the module fails
 * closed.
 */
public class ValkeyKeys {

  private final StringRedisTemplate valkey;
  private final String service;

  public ValkeyKeys(StringRedisTemplate valkey, String service) {
    this.valkey = valkey;
    this.service = service;
  }

  /**
   * The key of {@code id} of this kind, for the module's own values. An id that is personal data (a
   * phone number) is the module's to hash first.
   */
  public String key(String kind, String id) {
    if (!ValkeyConstants.NAME.matcher(kind).matches()) {
      throw new IllegalArgumentException(ValkeyConstants.KIND_INVALID + kind);
    }
    if (id.isBlank()) {
      throw new IllegalArgumentException(ValkeyConstants.ID_BLANK);
    }
    return ValkeyConstants.KEY_FORMAT.formatted(service, kind, id);
  }

  /** Counts one more in the window that started with the first count; the new count. */
  public long increment(String kind, String id, Duration window) {
    var seconds = seconds(window);
    var name = key(kind, id).getBytes(StandardCharsets.UTF_8);
    return Objects.requireNonNull(
        valkey.execute(
            (RedisCallback<Long>)
                connection -> {
                  var count = connection.stringCommands().incr(name);
                  connection.keyCommands().expire(name, seconds, ExpirationOptions.Condition.NX);
                  return count;
                }));
  }

  /**
   * Counts one more and checks it against {@code max}: empty while the count is within it, else how
   * long until the window ends (a {@code Retry-After}).
   */
  public Optional<Duration> hit(String kind, String id, long max, Duration window) {
    if (max < 1) {
      throw new IllegalArgumentException(ValkeyConstants.MAX_INVALID + max);
    }
    if (increment(kind, id, window) <= max) {
      return Optional.empty();
    }
    return Optional.of(expiresIn(kind, id).orElse(window));
  }

  /** The count in the current window; 0 when there is none. */
  public long count(String kind, String id) {
    var value = valkey.opsForValue().get(key(kind, id));
    return value == null ? 0 : Long.parseLong(value);
  }

  /** How long until the key ends; empty when there is no key or it never ends. */
  public Optional<Duration> expiresIn(String kind, String id) {
    var seconds = valkey.getExpire(key(kind, id));
    return seconds == null || seconds < 0
        ? Optional.empty()
        : Optional.of(Duration.ofSeconds(Math.max(1, seconds)));
  }

  /** Removes the key: a counter starts again from zero. */
  public void reset(String kind, String id) {
    valkey.delete(key(kind, id));
  }

  private static long seconds(Duration window) {
    if (window.toSeconds() < 1 || window.toNanosPart() != 0) {
      throw new IllegalArgumentException(ValkeyConstants.WINDOW_INVALID + window);
    }
    return window.toSeconds();
  }
}

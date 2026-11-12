package uz.khakimjanovich.platform.valkey;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ExpirationOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import uz.khakimjanovich.platform.error.GeneralException;

@Slf4j
public class ValkeyKeys {

  private final StringRedisTemplate valkey;
  private final String service;

  public ValkeyKeys(StringRedisTemplate valkey, String service) {
    this.valkey = valkey;
    this.service = service;
  }

  public String key(String kind, String id) {
    if (!ValkeyConstants.NAME.matcher(kind).matches() || id.isBlank()) {
      throw invalid();
    }
    return ValkeyConstants.KEY_FORMAT.formatted(service, kind, id);
  }

  public long increment(String kind, String id, Duration window) {
    var seconds = seconds(window);
    var name = key(kind, id).getBytes(StandardCharsets.UTF_8);
    return run(
        () ->
            valkey.execute(
                (RedisCallback<Long>)
                    connection -> {
                      var count = connection.stringCommands().incr(name);
                      connection
                          .keyCommands()
                          .expire(name, seconds, ExpirationOptions.Condition.NX);
                      return count;
                    }));
  }

  public Optional<Duration> hit(String kind, String id, long max, Duration window) {
    if (max < 1) {
      throw invalid();
    }
    if (increment(kind, id, window) <= max) {
      return Optional.empty();
    }
    return Optional.of(expiresIn(kind, id).orElse(window));
  }

  public long count(String kind, String id) {
    var name = key(kind, id);
    var value = run(() -> Optional.ofNullable(valkey.opsForValue().get(name)));
    return value.map(Long::parseLong).orElse(0L);
  }

  public Optional<Duration> expiresIn(String kind, String id) {
    var name = key(kind, id);
    var seconds = run(() -> valkey.getExpire(name));
    return seconds < 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(Math.max(1, seconds)));
  }

  public void reset(String kind, String id) {
    var name = key(kind, id);
    run(() -> valkey.delete(name));
  }

  private static long seconds(Duration window) {
    if (window.toSeconds() < 1 || window.toNanosPart() != 0) {
      throw invalid();
    }
    return window.toSeconds();
  }

  private static GeneralException invalid() {
    return new GeneralException(ValkeyExceptionStatus.VALKEY_INVALID_ARGUMENT);
  }

  private static <T> T run(Supplier<@Nullable T> call) {
    try {
      return Objects.requireNonNull(call.get());
    } catch (DataAccessException e) {
      log.warn(ValkeyConstants.LOG_UNAVAILABLE, e.getMessage(), e);
      throw new GeneralException(ValkeyExceptionStatus.VALKEY_UNAVAILABLE);
    }
  }
}

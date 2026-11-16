package uz.khakimjanovich.platform.valkey;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import uz.khakimjanovich.platform.error.GeneralException;

@Slf4j
public class ValkeyLocks {

  private static final RedisScript<Long> UNLOCK_SCRIPT =
      RedisScript.of(ValkeyConstants.UNLOCK_LUA, Long.class);

  private final StringRedisTemplate valkey;
  private final ValkeyKeys keys;

  public ValkeyLocks(StringRedisTemplate valkey, ValkeyKeys keys) {
    this.valkey = valkey;
    this.keys = keys;
  }

  public ValkeyLocks(StringRedisTemplate valkey, String service) {
    this(valkey, new ValkeyKeys(valkey, service));
  }

  public Optional<String> tryLock(String kind, String id, Duration ttl) {
    validateTtl(ttl);
    var key = keys.key(kind, id);
    var token = UUID.randomUUID().toString();
    var acquired = run(() -> valkey.opsForValue().setIfAbsent(key, token, ttl));
    return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
  }

  public Lock acquire(String kind, String id, Duration ttl) {
    var token =
        tryLock(kind, id, ttl)
            .orElseThrow(() -> new GeneralException(ValkeyExceptionStatus.VALKEY_LOCK_HELD));
    return new Lock(this, kind, id, token);
  }

  public boolean unlock(String kind, String id, String token) {
    if (token == null || token.isBlank()) {
      throw invalid();
    }
    var key = keys.key(kind, id);
    var result = run(() -> valkey.execute(UNLOCK_SCRIPT, List.of(key), token));
    return Long.valueOf(1L).equals(result);
  }

  public <T> T withLock(String kind, String id, Duration ttl, Supplier<T> action) {
    try (var lock = acquire(kind, id, ttl)) {
      log.trace("Acquired lock {} for {}/{}", lock.token(), kind, id);
      return action.get();
    }
  }

  public void withLock(String kind, String id, Duration ttl, Runnable action) {
    try (var lock = acquire(kind, id, ttl)) {
      log.trace("Acquired lock {} for {}/{}", lock.token(), kind, id);
      action.run();
    }
  }

  private static void validateTtl(Duration ttl) {
    if (ttl.toMillis() < 1 || ttl.toNanosPart() % 1_000_000 != 0) {
      throw invalid();
    }
  }

  private static GeneralException invalid() {
    return new GeneralException(ValkeyExceptionStatus.VALKEY_INVALID_ARGUMENT);
  }

  private static <T> @Nullable T run(Supplier<@Nullable T> call) {
    try {
      return call.get();
    } catch (DataAccessException e) {
      log.warn(ValkeyConstants.LOG_UNAVAILABLE, e.getMessage(), e);
      throw new GeneralException(ValkeyExceptionStatus.VALKEY_UNAVAILABLE);
    }
  }

  public record Lock(ValkeyLocks locks, String kind, String id, String token)
      implements AutoCloseable {

    @Override
    public void close() {
      locks.unlock(kind, id, token);
    }
  }
}

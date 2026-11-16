package uz.khakimjanovich.platform.valkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import uz.khakimjanovich.platform.error.GeneralException;

class ValkeyLocksTest {

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

  // Mockito cannot mock generic types without an unchecked cast
  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> ops = mock(ValueOperations.class);

  private final ValkeyLocks locks = new ValkeyLocks(redis, "payment-svc");

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(ops);
  }

  private static void invalid(ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus()).isEqualTo(ValkeyExceptionStatus.VALKEY_INVALID_ARGUMENT));
  }

  @Test
  void tryLockAcquiresLockAndReturnsToken() {
    when(ops.setIfAbsent(eq("payment-svc:payout:p1"), any(), eq(Duration.ofSeconds(10))))
        .thenReturn(true);

    var token = locks.tryLock("payout", "p1", Duration.ofSeconds(10));

    assertThat(token).isPresent().hasValueSatisfying(t -> assertThat(t).isNotBlank());
  }

  @Test
  void tryLockReturnsEmptyWhenLockIsAlreadyHeld() {
    when(ops.setIfAbsent(eq("payment-svc:payout:p1"), any(), eq(Duration.ofSeconds(10))))
        .thenReturn(false);

    var token = locks.tryLock("payout", "p1", Duration.ofSeconds(10));

    assertThat(token).isEmpty();
  }

  @Test
  void unlockExecutesLuaScriptAndReturnsTrueOnSuccess() {
    when(redis.execute(any(), eq(List.of("payment-svc:payout:p1")), eq("token-123")))
        .thenReturn(1L);

    var released = locks.unlock("payout", "p1", "token-123");

    assertThat(released).isTrue();
  }

  @Test
  void unlockReturnsFalseWhenScriptReportsKeyNotDeleted() {
    when(redis.execute(any(), eq(List.of("payment-svc:payout:p1")), eq("token-123")))
        .thenReturn(0L);

    var released = locks.unlock("payout", "p1", "token-123");

    assertThat(released).isFalse();
  }

  @Test
  void unlockRefusesBlankToken() {
    invalid(() -> locks.unlock("payout", "p1", ""));
    invalid(() -> locks.unlock("payout", "p1", "  "));
  }

  @Test
  void tryLockRefusesInvalidTtlOrKindOrId() {
    invalid(() -> locks.tryLock("payout", "p1", Duration.ZERO));
    invalid(() -> locks.tryLock("payout", "p1", Duration.ofMillis(-10)));
    invalid(() -> locks.tryLock("payout", "p1", Duration.ofNanos(500)));
    invalid(() -> locks.tryLock("payout:invalid", "p1", Duration.ofSeconds(10)));
    invalid(() -> locks.tryLock("payout", " ", Duration.ofSeconds(10)));
  }

  @Test
  void withLockRunsActionAndReleasesLock() {
    when(ops.setIfAbsent(eq("payment-svc:payout:p1"), any(), eq(Duration.ofSeconds(5))))
        .thenReturn(true);
    when(redis.execute(any(), eq(List.of("payment-svc:payout:p1")), any())).thenReturn(1L);

    var executed = new AtomicBoolean(false);
    var result =
        locks.withLock(
            "payout",
            "p1",
            Duration.ofSeconds(5),
            () -> {
              executed.set(true);
              return "done";
            });

    assertThat(result).isEqualTo("done");
    assertThat(executed).isTrue();
    verify(redis).execute(any(), eq(List.of("payment-svc:payout:p1")), any());
  }

  @Test
  void withLockRunnableRunsActionAndReleasesLock() {
    when(ops.setIfAbsent(eq("payment-svc:payout:p1"), any(), eq(Duration.ofSeconds(5))))
        .thenReturn(true);
    when(redis.execute(any(), eq(List.of("payment-svc:payout:p1")), any())).thenReturn(1L);

    var executed = new AtomicBoolean(false);
    locks.withLock("payout", "p1", Duration.ofSeconds(5), () -> executed.set(true));

    assertThat(executed).isTrue();
    verify(redis).execute(any(), eq(List.of("payment-svc:payout:p1")), any());
  }

  @Test
  void withLockThrowsLockHeldWhenLockCannotBeAcquired() {
    when(ops.setIfAbsent(eq("payment-svc:payout:p1"), any(), eq(Duration.ofSeconds(5))))
        .thenReturn(false);

    assertThatThrownBy(() -> locks.withLock("payout", "p1", Duration.ofSeconds(5), () -> "never"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(ValkeyExceptionStatus.VALKEY_LOCK_HELD));
  }

  @Test
  void withLockReleasesLockEvenIfActionFails() {
    when(ops.setIfAbsent(eq("payment-svc:payout:p1"), any(), eq(Duration.ofSeconds(5))))
        .thenReturn(true);
    when(redis.execute(any(), eq(List.of("payment-svc:payout:p1")), any())).thenReturn(1L);

    assertThatThrownBy(
            () ->
                locks.withLock(
                    "payout",
                    "p1",
                    Duration.ofSeconds(5),
                    () -> {
                      throw new IllegalStateException("action failed");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("action failed");

    verify(redis).execute(any(), eq(List.of("payment-svc:payout:p1")), any());
  }

  @Test
  void valkeyUnavailableWhenDataAccessExceptionOccurs() {
    when(ops.setIfAbsent(any(), any(), any(Duration.class)))
        .thenThrow(new QueryTimeoutException("timed out"));
    when(redis.execute(any(), anyList(), any())).thenThrow(new QueryTimeoutException("timed out"));

    assertThatThrownBy(() -> locks.tryLock("payout", "p1", Duration.ofSeconds(5)))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(ValkeyExceptionStatus.VALKEY_UNAVAILABLE));

    assertThatThrownBy(() -> locks.unlock("payout", "p1", "token-1"))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(ValkeyExceptionStatus.VALKEY_UNAVAILABLE));
  }
}

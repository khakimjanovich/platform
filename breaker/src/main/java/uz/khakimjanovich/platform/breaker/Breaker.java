package uz.khakimjanovich.platform.breaker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import uz.khakimjanovich.platform.error.ErrorExceptionStatus;
import uz.khakimjanovich.platform.error.GeneralException;

public final class Breaker {

  public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private final int failures;
  private final Duration probeAfter;
  private final Clock clock;
  private final Predicate<Throwable> failurePredicate;
  private int failuresInARow;
  private @Nullable Instant openedAt;
  private boolean probing;

  public Breaker(int failures, Duration probeAfter) {
    this(failures, probeAfter, Clock.systemUTC(), BreakerPredicates.defaultPredicate());
  }

  public Breaker(int failures, Duration probeAfter, Clock clock) {
    this(failures, probeAfter, clock, BreakerPredicates.defaultPredicate());
  }

  public Breaker(
      int failures, Duration probeAfter, Clock clock, Predicate<Throwable> failurePredicate) {
    if (failures < 1) {
      throw new GeneralException(ErrorExceptionStatus.INTERNAL_ERROR)
          .subError("failures", "must be at least 1, not " + failures);
    }
    if (!probeAfter.isPositive()) {
      throw new GeneralException(ErrorExceptionStatus.INTERNAL_ERROR)
          .subError("probeAfter", "must be positive, not " + probeAfter);
    }
    this.failures = failures;
    this.probeAfter = probeAfter;
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.failurePredicate =
        Objects.requireNonNull(failurePredicate, "failurePredicate must not be null");
  }

  public synchronized boolean tryAcquire() {
    if (openedAt == null) {
      return true;
    }
    if (!clock.instant().isBefore(openedAt.plus(probeAfter))) {
      probing = true;
      openedAt = clock.instant();
      return true;
    }
    return false;
  }

  public synchronized void recordSuccess() {
    failuresInARow = 0;
    openedAt = null;
    probing = false;
  }

  public synchronized void recordFailure() {
    failuresInARow++;
    if (probing || failuresInARow >= failures) {
      openedAt = clock.instant();
      probing = false;
    }
  }

  public void recordFailure(Throwable throwable) {
    if (failurePredicate.test(throwable)) {
      recordFailure();
    } else {
      recordSuccess();
    }
  }

  public <T> T execute(Supplier<T> action) {
    return execute(action, failurePredicate);
  }

  public <T> T execute(Supplier<T> action, Predicate<Throwable> isFailure) {
    if (!tryAcquire()) {
      Duration retry = remainingProbeDelay();
      if (retry.isZero()) {
        retry = probeAfter;
      }
      throw new GeneralException(BreakerExceptionStatus.BREAKER_OPEN)
          .subError("breaker", "open")
          .retryAfter(retry);
    }
    T result;
    try {
      result = action.get();
    } catch (RuntimeException | Error ex) {
      if (isFailure.test(ex)) {
        recordFailure();
      } else {
        recordSuccess();
      }
      throw ex;
    }
    recordSuccess();
    return result;
  }

  public void run(Runnable action) {
    run(action, failurePredicate);
  }

  public void run(Runnable action, Predicate<Throwable> isFailure) {
    execute(
        () -> {
          action.run();
          return null;
        },
        isFailure);
  }

  public synchronized State state() {
    if (openedAt == null) {
      return State.CLOSED;
    }
    if (probing) {
      return State.HALF_OPEN;
    }
    return State.OPEN;
  }

  public synchronized boolean isOpen() {
    return state() == State.OPEN;
  }

  public synchronized boolean isClosed() {
    return state() == State.CLOSED;
  }

  public synchronized boolean isHalfOpen() {
    return state() == State.HALF_OPEN;
  }

  public synchronized Duration remainingProbeDelay() {
    if (openedAt == null) {
      return Duration.ZERO;
    }
    Duration remaining = Duration.between(clock.instant(), openedAt.plus(probeAfter));
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }

  public int failures() {
    return failures;
  }

  public Duration probeAfter() {
    return probeAfter;
  }

  public Predicate<Throwable> failurePredicate() {
    return failurePredicate;
  }
}

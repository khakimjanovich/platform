package uz.khakimjanovich.platform.breaker;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import uz.khakimjanovich.platform.error.ErrorExceptionStatus;
import uz.khakimjanovich.platform.error.GeneralException;

public record BreakerConfig(
    String name, int failures, Duration probeAfter, Predicate<Throwable> failurePredicate) {

  public BreakerConfig {
    Objects.requireNonNull(name, "name must not be null");
    if (name.isBlank()) {
      throw new GeneralException(ErrorExceptionStatus.INTERNAL_ERROR)
          .subError("name", "must not be blank");
    }
    if (failures < 1) {
      throw new GeneralException(ErrorExceptionStatus.INTERNAL_ERROR)
          .subError("failures", "must be at least 1, not " + failures);
    }
    if (!probeAfter.isPositive()) {
      throw new GeneralException(ErrorExceptionStatus.INTERNAL_ERROR)
          .subError("probeAfter", "must be positive, not " + probeAfter);
    }
    Objects.requireNonNull(failurePredicate, "failurePredicate must not be null");
  }

  public BreakerConfig(int failures, Duration probeAfter, Predicate<Throwable> failurePredicate) {
    this(BreakerConstants.DEFAULT_BREAKER_NAME, failures, probeAfter, failurePredicate);
  }

  public static BreakerConfig of(int failures, Duration probeAfter) {
    return new BreakerConfig(
        BreakerConstants.DEFAULT_BREAKER_NAME,
        failures,
        probeAfter,
        BreakerPredicates.defaultPredicate());
  }

  public static BreakerConfig of(String name, int failures, Duration probeAfter) {
    return new BreakerConfig(name, failures, probeAfter, BreakerPredicates.defaultPredicate());
  }

  public static BreakerConfig of(
      int failures, Duration probeAfter, Predicate<Throwable> failurePredicate) {
    return new BreakerConfig(
        BreakerConstants.DEFAULT_BREAKER_NAME, failures, probeAfter, failurePredicate);
  }

  public static BreakerConfig of(
      String name, int failures, Duration probeAfter, Predicate<Throwable> failurePredicate) {
    return new BreakerConfig(name, failures, probeAfter, failurePredicate);
  }

  public static BreakerConfig from(BreakerConfig base) {
    return new BreakerConfig(
        base.name(), base.failures(), base.probeAfter(), base.failurePredicate());
  }

  public BreakerConfig withName(String name) {
    return new BreakerConfig(name, this.failures, this.probeAfter, this.failurePredicate);
  }

  public BreakerConfig withFailures(int failures) {
    return new BreakerConfig(this.name, failures, this.probeAfter, this.failurePredicate);
  }

  public BreakerConfig withProbeAfter(Duration probeAfter) {
    return new BreakerConfig(this.name, this.failures, probeAfter, this.failurePredicate);
  }

  public BreakerConfig withFailurePredicate(Predicate<Throwable> failurePredicate) {
    return new BreakerConfig(this.name, this.failures, this.probeAfter, failurePredicate);
  }

  public Breaker create() {
    return create(Clock.systemUTC());
  }

  public Breaker create(Clock clock) {
    return new Breaker(name, failures, probeAfter, clock, failurePredicate);
  }

  public Breaker create(String name) {
    return create(name, Clock.systemUTC());
  }

  public Breaker create(String name, Clock clock) {
    return new Breaker(name, failures, probeAfter, clock, failurePredicate);
  }
}

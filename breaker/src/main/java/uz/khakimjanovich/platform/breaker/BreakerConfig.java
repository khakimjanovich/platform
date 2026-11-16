package uz.khakimjanovich.platform.breaker;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import uz.khakimjanovich.platform.error.ErrorExceptionStatus;
import uz.khakimjanovich.platform.error.GeneralException;

public record BreakerConfig(
    int failures, Duration probeAfter, Predicate<Throwable> failurePredicate) {

  public BreakerConfig {
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

  public static BreakerConfig of(int failures, Duration probeAfter) {
    return new BreakerConfig(failures, probeAfter, BreakerPredicates.defaultPredicate());
  }

  public static BreakerConfig of(
      int failures, Duration probeAfter, Predicate<Throwable> failurePredicate) {
    return new BreakerConfig(failures, probeAfter, failurePredicate);
  }

  public static BreakerConfig from(BreakerConfig base) {
    return new BreakerConfig(base.failures(), base.probeAfter(), base.failurePredicate());
  }

  public BreakerConfig withFailures(int failures) {
    return new BreakerConfig(failures, this.probeAfter, this.failurePredicate);
  }

  public BreakerConfig withProbeAfter(Duration probeAfter) {
    return new BreakerConfig(this.failures, probeAfter, this.failurePredicate);
  }

  public BreakerConfig withFailurePredicate(Predicate<Throwable> failurePredicate) {
    return new BreakerConfig(this.failures, this.probeAfter, failurePredicate);
  }

  public Breaker create() {
    return create(Clock.systemUTC());
  }

  public Breaker create(Clock clock) {
    return new Breaker(failures, probeAfter, clock, failurePredicate);
  }
}

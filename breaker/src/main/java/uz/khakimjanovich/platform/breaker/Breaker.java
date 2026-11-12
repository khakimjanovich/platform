package uz.khakimjanovich.platform.breaker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import uz.khakimjanovich.platform.error.ErrorExceptionStatus;
import uz.khakimjanovich.platform.error.GeneralException;

public final class Breaker {

  private final int failures;
  private final Duration probeAfter;
  private final Clock clock;
  private int failuresInARow;
  private @Nullable Instant openedAt;
  private boolean probing;

  public Breaker(int failures, Duration probeAfter, Clock clock) {
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
    this.clock = clock;
  }

  public synchronized boolean allows() {
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

  public synchronized void answered() {
    failuresInARow = 0;
    openedAt = null;
    probing = false;
  }

  public synchronized void failed() {
    failuresInARow++;
    if (probing || failuresInARow >= failures) {
      openedAt = clock.instant();
      probing = false;
    }
  }

  public synchronized boolean isOpen() {
    return openedAt != null;
  }

  public int failures() {
    return failures;
  }
}

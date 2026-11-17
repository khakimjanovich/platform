package uz.khakimjanovich.platform.breaker;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import java.util.Objects;

public final class BreakerMeterBinder implements MeterBinder {

  private final Breaker breaker;
  private final Iterable<Tag> tags;

  public BreakerMeterBinder(Breaker breaker) {
    this(breaker, List.of());
  }

  public BreakerMeterBinder(Breaker breaker, Iterable<Tag> tags) {
    this.breaker = Objects.requireNonNull(breaker, "breaker must not be null");
    this.tags = Objects.requireNonNull(tags, "tags must not be null");
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Tags baseTags = Tags.concat(tags, BreakerConstants.NAME_TAG, breaker.name());

    Gauge.builder(BreakerConstants.BREAKER_STATE_METRIC, breaker, b -> stateToValue(b.state()))
        .description("State of the circuit breaker (0 = CLOSED, 1 = HALF_OPEN, 2 = OPEN)")
        .tags(baseTags)
        .register(registry);

    Gauge.builder(BreakerConstants.BREAKER_FAILURES_METRIC, breaker, Breaker::consecutiveFailures)
        .description("Consecutive failure count recorded by the circuit breaker")
        .tags(baseTags)
        .register(registry);

    Gauge.builder(BreakerConstants.BREAKER_THRESHOLD_METRIC, breaker, Breaker::failures)
        .description("Configured failure threshold to trip the circuit breaker")
        .tags(baseTags)
        .register(registry);

    FunctionCounter.builder(
            BreakerConstants.BREAKER_CALLS_METRIC, breaker, Breaker::successfulCalls)
        .description("Total calls executed by the circuit breaker")
        .tags(baseTags.and(BreakerConstants.RESULT_TAG, BreakerConstants.RESULT_SUCCESS))
        .register(registry);

    FunctionCounter.builder(BreakerConstants.BREAKER_CALLS_METRIC, breaker, Breaker::failedCalls)
        .description("Total calls executed by the circuit breaker")
        .tags(baseTags.and(BreakerConstants.RESULT_TAG, BreakerConstants.RESULT_FAILURE))
        .register(registry);

    FunctionCounter.builder(BreakerConstants.BREAKER_CALLS_METRIC, breaker, Breaker::rejectedCalls)
        .description("Total calls executed by the circuit breaker")
        .tags(baseTags.and(BreakerConstants.RESULT_TAG, BreakerConstants.RESULT_REJECTED))
        .register(registry);
  }

  private static double stateToValue(Breaker.State state) {
    return switch (state) {
      case CLOSED -> 0.0;
      case HALF_OPEN -> 1.0;
      case OPEN -> 2.0;
    };
  }
}

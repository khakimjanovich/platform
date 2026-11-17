package uz.khakimjanovich.platform.breaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import uz.khakimjanovich.platform.error.GeneralException;

class BreakerMeterBinderTest {

  final BreakerTest.TestClock clock = new BreakerTest.TestClock();

  @Test
  void bindsMetersAndTracksLifecycleAccurately() {
    var registry = new SimpleMeterRegistry();
    var breaker = new Breaker("test-gateway", 2, Duration.ofSeconds(10), clock);
    var binder = new BreakerMeterBinder(breaker, List.of(Tag.of("region", "uz")));
    binder.bindTo(registry);

    // Initial state
    var stateGauge =
        registry
            .get(BreakerConstants.BREAKER_STATE_METRIC)
            .tag(BreakerConstants.NAME_TAG, "test-gateway")
            .tag("region", "uz")
            .gauge();
    var failuresGauge =
        registry
            .get(BreakerConstants.BREAKER_FAILURES_METRIC)
            .tag(BreakerConstants.NAME_TAG, "test-gateway")
            .gauge();
    var thresholdGauge =
        registry
            .get(BreakerConstants.BREAKER_THRESHOLD_METRIC)
            .tag(BreakerConstants.NAME_TAG, "test-gateway")
            .gauge();

    var successCounter =
        registry
            .get(BreakerConstants.BREAKER_CALLS_METRIC)
            .tag(BreakerConstants.NAME_TAG, "test-gateway")
            .tag(BreakerConstants.RESULT_TAG, BreakerConstants.RESULT_SUCCESS)
            .functionCounter();
    var failureCounter =
        registry
            .get(BreakerConstants.BREAKER_CALLS_METRIC)
            .tag(BreakerConstants.NAME_TAG, "test-gateway")
            .tag(BreakerConstants.RESULT_TAG, BreakerConstants.RESULT_FAILURE)
            .functionCounter();
    var rejectedCounter =
        registry
            .get(BreakerConstants.BREAKER_CALLS_METRIC)
            .tag(BreakerConstants.NAME_TAG, "test-gateway")
            .tag(BreakerConstants.RESULT_TAG, BreakerConstants.RESULT_REJECTED)
            .functionCounter();

    assertThat(stateGauge.value()).isEqualTo(0.0); // CLOSED
    assertThat(failuresGauge.value()).isEqualTo(0.0);
    assertThat(thresholdGauge.value()).isEqualTo(2.0);
    assertThat(successCounter.count()).isEqualTo(0.0);
    assertThat(failureCounter.count()).isEqualTo(0.0);
    assertThat(rejectedCounter.count()).isEqualTo(0.0);

    // 1. Success call
    breaker.execute(() -> "ok");
    assertThat(successCounter.count()).isEqualTo(1.0);
    assertThat(stateGauge.value()).isEqualTo(0.0);

    // 2. 1st Failure
    assertThatThrownBy(
            () ->
                breaker.execute(
                    () -> {
                      throw new ResourceAccessException("down");
                    }))
        .isInstanceOf(ResourceAccessException.class);
    assertThat(failureCounter.count()).isEqualTo(1.0);
    assertThat(failuresGauge.value()).isEqualTo(1.0);
    assertThat(stateGauge.value()).isEqualTo(0.0);

    // 3. 2nd Failure trips the breaker to OPEN
    assertThatThrownBy(
            () ->
                breaker.execute(
                    () -> {
                      throw new ResourceAccessException("down again");
                    }))
        .isInstanceOf(ResourceAccessException.class);
    assertThat(failureCounter.count()).isEqualTo(2.0);
    assertThat(failuresGauge.value()).isEqualTo(2.0);
    assertThat(stateGauge.value()).isEqualTo(2.0); // OPEN

    // 4. Rejected call while OPEN
    assertThatThrownBy(() -> breaker.execute(() -> "never")).isInstanceOf(GeneralException.class);
    assertThat(rejectedCounter.count()).isEqualTo(1.0);

    // 5. Advance clock beyond probe delay -> probe transitions to HALF_OPEN
    clock.now = clock.now.plus(Duration.ofSeconds(15));
    breaker.tryAcquire();
    assertThat(stateGauge.value()).isEqualTo(1.0); // HALF_OPEN

    // 6. Record success in probe -> recovers to CLOSED
    breaker.recordSuccess();
    assertThat(stateGauge.value()).isEqualTo(0.0); // CLOSED
    assertThat(failuresGauge.value()).isEqualTo(0.0);
    assertThat(successCounter.count()).isEqualTo(2.0);
  }
}

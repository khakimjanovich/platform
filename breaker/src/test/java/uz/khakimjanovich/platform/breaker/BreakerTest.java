package uz.khakimjanovich.platform.breaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import uz.khakimjanovich.platform.error.GeneralException;

class BreakerTest {

  static final class TestClock extends Clock {
    Instant now = Instant.parse("2026-11-10T10:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  final TestClock clock = new TestClock();
  final Breaker breaker = new Breaker(5, Duration.ofSeconds(30), clock);

  @Test
  void fiveFailuresInARowOpenItAndOneProbeGoesAfter30s() {
    for (var i = 0; i < 4; i++) {
      breaker.failed();
    }
    assertThat(breaker.allows()).isTrue();
    breaker.failed();
    assertThat(breaker.isOpen()).isTrue();
    assertThat(breaker.allows()).isFalse();

    clock.now = clock.now.plusSeconds(30);
    assertThat(breaker.allows()).as("the probe").isTrue();
    assertThat(breaker.allows()).as("only one probe").isFalse();
    breaker.failed();
    assertThat(breaker.allows()).as("a failed probe opens it again").isFalse();

    clock.now = clock.now.plusSeconds(30);
    assertThat(breaker.allows()).isTrue();
    breaker.answered();
    assertThat(breaker.isOpen()).isFalse();
    assertThat(breaker.allows()).isTrue();
  }

  @Test
  void anAnswerResetsTheCount() {
    for (var i = 0; i < 4; i++) {
      breaker.failed();
    }
    breaker.answered();
    breaker.failed();
    assertThat(breaker.isOpen()).isFalse();
  }

  @Test
  void aProbeNeverReportedIsRetriedAfterTheNextPeriod() {
    for (var i = 0; i < 5; i++) {
      breaker.failed();
    }
    clock.now = clock.now.plusSeconds(30);
    assertThat(breaker.allows()).as("the probe, never reported").isTrue();
    clock.now = clock.now.plusSeconds(29);
    assertThat(breaker.allows()).isFalse();
    clock.now = clock.now.plusSeconds(1);
    assertThat(breaker.allows()).as("the next probe").isTrue();
  }

  @Test
  void noFailureCountBelowOne() {
    assertThatThrownBy(() -> new Breaker(0, Duration.ofSeconds(30), clock))
        .isInstanceOf(GeneralException.class);
  }

  @Test
  void noProbePeriodBelowOneNanosecond() {
    assertThatThrownBy(() -> new Breaker(5, Duration.ZERO, clock))
        .isInstanceOf(GeneralException.class);
  }
}

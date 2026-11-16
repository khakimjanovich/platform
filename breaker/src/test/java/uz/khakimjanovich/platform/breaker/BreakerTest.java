package uz.khakimjanovich.platform.breaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
  void stateTransitionsBetweenClosedOpenAndHalfOpen() {
    assertThat(breaker.state()).isEqualTo(Breaker.State.CLOSED);
    assertThat(breaker.isClosed()).isTrue();
    assertThat(breaker.isOpen()).isFalse();
    assertThat(breaker.isHalfOpen()).isFalse();

    for (var i = 0; i < 4; i++) {
      breaker.failed();
      assertThat(breaker.state()).isEqualTo(Breaker.State.CLOSED);
      assertThat(breaker.isClosed()).isTrue();
    }

    breaker.failed();
    assertThat(breaker.state()).isEqualTo(Breaker.State.OPEN);
    assertThat(breaker.isOpen()).isTrue();
    assertThat(breaker.isClosed()).isFalse();
    assertThat(breaker.isHalfOpen()).isFalse();

    clock.now = clock.now.plusSeconds(30);
    assertThat(breaker.allows()).as("canary probe permitted").isTrue();
    assertThat(breaker.state()).isEqualTo(Breaker.State.HALF_OPEN);
    assertThat(breaker.isHalfOpen()).isTrue();
    assertThat(breaker.isOpen()).isTrue();
    assertThat(breaker.isClosed()).isFalse();

    breaker.failed();
    assertThat(breaker.state()).isEqualTo(Breaker.State.OPEN);
    assertThat(breaker.isOpen()).isTrue();
    assertThat(breaker.isHalfOpen()).isFalse();

    clock.now = clock.now.plusSeconds(30);
    assertThat(breaker.allows()).isTrue();
    assertThat(breaker.state()).isEqualTo(Breaker.State.HALF_OPEN);
    breaker.answered();
    assertThat(breaker.state()).isEqualTo(Breaker.State.CLOSED);
    assertThat(breaker.isClosed()).isTrue();
    assertThat(breaker.isOpen()).isFalse();
    assertThat(breaker.isHalfOpen()).isFalse();
  }

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

  @Test
  void configPrototypeDerivationAndValidation() {
    var base = BreakerConfig.of(5, Duration.ofSeconds(30));
    assertThat(base.failures()).isEqualTo(5);
    assertThat(base.probeAfter()).isEqualTo(Duration.ofSeconds(30));

    var derived = BreakerConfig.from(base).withFailures(3).withProbeAfter(Duration.ofSeconds(5));
    assertThat(derived.failures()).isEqualTo(3);
    assertThat(derived.probeAfter()).isEqualTo(Duration.ofSeconds(5));

    var created = derived.create(clock);
    assertThat(created.failures()).isEqualTo(3);
    assertThat(created.probeAfter()).isEqualTo(Duration.ofSeconds(5));

    assertThatThrownBy(() -> BreakerConfig.of(0, Duration.ofSeconds(30)))
        .isInstanceOf(GeneralException.class);
    assertThatThrownBy(() -> BreakerConfig.of(5, Duration.ZERO))
        .isInstanceOf(GeneralException.class);
  }

  @Test
  void predicatesClassifyInfrastructureVersusClientErrors() {
    assertThat(BreakerPredicates.isFailure(new IOException("connection reset"))).isTrue();
    assertThat(BreakerPredicates.isFailure(new TimeoutException("timeout"))).isTrue();
    assertThat(BreakerPredicates.isFailure(new ResourceAccessException("socket timeout"))).isTrue();
    assertThat(
            BreakerPredicates.isFailure(
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)))
        .isTrue();
    assertThat(BreakerPredicates.isFailure(new HttpServerErrorException(HttpStatus.BAD_GATEWAY)))
        .isTrue();

    assertThat(BreakerPredicates.isFailure(new HttpClientErrorException(HttpStatus.BAD_REQUEST)))
        .isFalse();
    assertThat(
            BreakerPredicates.isFailure(
                new HttpClientErrorException(HttpStatus.UNPROCESSABLE_CONTENT)))
        .isFalse();
    assertThat(BreakerPredicates.isFailure(new IllegalArgumentException("invalid PIN"))).isFalse();

    assertThat(BreakerPredicates.isFailure(new RuntimeException(new IOException("broken"))))
        .isTrue();
  }

  @Test
  void executeReturnsValueAndRecordsAnswerOnSuccess() {
    var result = breaker.execute(() -> "success");
    assertThat(result).isEqualTo("success");
    assertThat(breaker.isClosed()).isTrue();
  }

  @Test
  void executeTripsBreakerOnInfrastructureFailures() {
    var b = new Breaker(2, Duration.ofSeconds(10), clock);

    assertThatThrownBy(
            () ->
                b.execute(
                    () -> {
                      throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
                    }))
        .isInstanceOf(HttpServerErrorException.class);
    assertThat(b.isClosed()).isTrue();

    assertThatThrownBy(
            () ->
                b.execute(
                    () -> {
                      throw new ResourceAccessException("timed out");
                    }))
        .isInstanceOf(ResourceAccessException.class);
    assertThat(b.isOpen()).isTrue();

    assertThatThrownBy(() -> b.execute(() -> "never-run")).isInstanceOf(GeneralException.class);
  }

  @Test
  void executeDoesNotTripOnClientErrorsAndKeepsBreakerClosed() {
    var b = new Breaker(2, Duration.ofSeconds(10), clock);

    for (var i = 0; i < 5; i++) {
      assertThatThrownBy(
              () ->
                  b.execute(
                      () -> {
                        throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_CONTENT);
                      }))
          .isInstanceOf(HttpClientErrorException.class);
    }

    assertThat(b.isClosed()).isTrue();
    assertThat(b.isOpen()).isFalse();
  }

  @Test
  void runExecutesVoidOperationsSafely() {
    var counter = new AtomicInteger();
    breaker.run(counter::incrementAndGet);
    assertThat(counter).hasValue(1);
  }
}

package uz.khakimjanovich.platform.breaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
  final Breaker paymentRailBreaker = new Breaker(3, Duration.ofSeconds(5), clock);

  @Test
  void schemeOutageTripsRailAndShedsSubsequentAuthorizationsWithDynamicRetryAfter() {
    // Stage 1: Card scheme experiences 2 transient socket timeouts (still below trip threshold of
    // 3)
    for (var i = 0; i < 2; i++) {
      assertThatThrownBy(
              () ->
                  paymentRailBreaker.execute(
                      () -> {
                        throw new ResourceAccessException(
                            "Connect timed out to card scheme switch");
                      }))
          .isInstanceOf(ResourceAccessException.class);
      assertThat(paymentRailBreaker.isClosed()).isTrue();
      assertThat(paymentRailBreaker.state()).isEqualTo(Breaker.State.CLOSED);
    }

    // Stage 2: 3rd consecutive timeout trips the rail immediately into OPEN
    assertThatThrownBy(
            () ->
                paymentRailBreaker.execute(
                    () -> {
                      throw new ResourceAccessException("Socket reset by peer");
                    }))
        .isInstanceOf(ResourceAccessException.class);

    assertThat(paymentRailBreaker.isOpen()).isTrue();
    assertThat(paymentRailBreaker.state()).isEqualTo(Breaker.State.OPEN);

    // Stage 3: Immediate next authorization is shed without invoking external I/O
    assertThatThrownBy(() -> paymentRailBreaker.execute(() -> "never-invoked"))
        .isInstanceOf(GeneralException.class)
        .satisfies(
            ex -> {
              var ge = (GeneralException) ex;
              assertThat(ge.getStatus()).isEqualTo(BreakerExceptionStatus.BREAKER_OPEN);
              assertThat(ge.getRetryAfter()).isEqualTo(Duration.ofSeconds(5));
            });

    // Stage 4: Advancing clock by 3s updates dynamic remaining cooldown to 2s
    clock.now = clock.now.plusSeconds(3);
    assertThatThrownBy(() -> paymentRailBreaker.execute(() -> "never-invoked"))
        .isInstanceOf(GeneralException.class)
        .satisfies(
            ex -> {
              var ge = (GeneralException) ex;
              assertThat(ge.getRetryAfter()).isEqualTo(Duration.ofSeconds(2));
            });
  }

  @Test
  void businessDeclinesAndInvalidCardsNeverTripTheRail() {
    // 50 cardholder business rejections in a row: wrong PINs, expired cards, insufficient funds
    for (var i = 0; i < 50; i++) {
      HttpStatus status = (i % 2 == 0) ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.UNAUTHORIZED;
      assertThatThrownBy(
              () ->
                  paymentRailBreaker.execute(
                      () -> {
                        throw new HttpClientErrorException(status, "Cardholder declined");
                      }))
          .isInstanceOf(HttpClientErrorException.class);

      assertThat(paymentRailBreaker.isClosed()).isTrue();
      assertThat(paymentRailBreaker.isOpen()).isFalse();
    }

    // Subsequent valid card authorization succeeds seamlessly
    var authCode = paymentRailBreaker.execute(() -> "AUTH-987654");
    assertThat(authCode).isEqualTo("AUTH-987654");
    assertThat(paymentRailBreaker.isClosed()).isTrue();
  }

  @Test
  void halfOpenStateAllowsExactlyOneCanaryAuthorizationAndShedsConcurrentTraffic() {
    // Trip the rail
    for (var i = 0; i < 3; i++) {
      paymentRailBreaker.failed();
    }
    assertThat(paymentRailBreaker.isOpen()).isTrue();

    // Advance past probeAfter (5 seconds)
    clock.now = clock.now.plusSeconds(5);

    // First call is permitted through as a canary probe
    assertThat(paymentRailBreaker.allows()).as("Canary probe permitted").isTrue();
    assertThat(paymentRailBreaker.state()).isEqualTo(Breaker.State.HALF_OPEN);
    assertThat(paymentRailBreaker.isHalfOpen()).isTrue();

    // While canary probe is active in-flight, concurrent calls are strictly shed
    assertThat(paymentRailBreaker.allows()).as("Concurrent canary traffic shed").isFalse();
    assertThat(paymentRailBreaker.state()).isEqualTo(Breaker.State.HALF_OPEN);
  }

  @Test
  void canarySuccessRestoresFullAcquiringTraffic() {
    // Trip the rail
    for (var i = 0; i < 3; i++) {
      paymentRailBreaker.failed();
    }
    clock.now = clock.now.plusSeconds(5);

    // Canary probe successfully executes
    var result = paymentRailBreaker.execute(() -> "CANARY-APPROVED");
    assertThat(result).isEqualTo("CANARY-APPROVED");

    // Circuit is healed back to CLOSED
    assertThat(paymentRailBreaker.isClosed()).isTrue();
    assertThat(paymentRailBreaker.state()).isEqualTo(Breaker.State.CLOSED);
    assertThat(paymentRailBreaker.isOpen()).isFalse();
    assertThat(paymentRailBreaker.isHalfOpen()).isFalse();

    // Subsequent authorizations proceed freely
    assertThat(paymentRailBreaker.allows()).isTrue();
  }

  @Test
  void canaryFailureImmediatelyReTripsRailAndRenewsCoolingPeriod() {
    // Trip the rail
    for (var i = 0; i < 3; i++) {
      paymentRailBreaker.failed();
    }
    clock.now = clock.now.plusSeconds(5);

    // Canary probe attempts execution but card scheme is still down
    assertThatThrownBy(
            () ->
                paymentRailBreaker.execute(
                    () -> {
                      throw new HttpServerErrorException(
                          HttpStatus.BAD_GATEWAY, "Card Scheme Down");
                    }))
        .isInstanceOf(HttpServerErrorException.class);

    // Rail immediately reverts to OPEN
    assertThat(paymentRailBreaker.isOpen()).isTrue();
    assertThat(paymentRailBreaker.state()).isEqualTo(Breaker.State.OPEN);
    assertThat(paymentRailBreaker.isHalfOpen()).isFalse();

    // Cooldown is renewed: no traffic allowed for another 5s
    assertThat(paymentRailBreaker.allows()).isFalse();
    clock.now = clock.now.plusSeconds(4);
    assertThat(paymentRailBreaker.allows()).isFalse();

    clock.now = clock.now.plusSeconds(1);
    assertThat(paymentRailBreaker.allows()).as("Renewed probe permitted").isTrue();
  }

  @Test
  void abandonedOrHangingCanaryIsSafelyReplacedAfterTimeout() {
    for (var i = 0; i < 3; i++) {
      paymentRailBreaker.failed();
    }
    clock.now = clock.now.plusSeconds(5);

    // Thread starts canary probe but dies / hangs without reporting answered() or failed()
    assertThat(paymentRailBreaker.allows()).isTrue();
    assertThat(paymentRailBreaker.state()).isEqualTo(Breaker.State.HALF_OPEN);

    // During probeAfter window, concurrent traffic remains shed
    clock.now = clock.now.plusSeconds(4);
    assertThat(paymentRailBreaker.allows()).isFalse();

    // Once probeAfter elapses again, the abandoned canary is replaced by a fresh canary
    clock.now = clock.now.plusSeconds(1);
    assertThat(paymentRailBreaker.allows()).as("Abandoned canary replaced").isTrue();
  }

  @Test
  void concurrentTrafficExhibitsRaceFreeStateTransitionsUnderContention() throws Exception {
    var breaker = new Breaker(5, Duration.ofMillis(50), clock);
    var threads = 16;
    var iterations = 200;
    var executor = Executors.newFixedThreadPool(threads);
    var startLatch = new CountDownLatch(1);
    var futures = new ArrayList<Future<?>>();
    var allowedCount = new AtomicInteger();
    var rejectedCount = new AtomicInteger();

    for (var t = 0; t < threads; t++) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  for (var i = 0; i < iterations; i++) {
                    if (breaker.allows()) {
                      allowedCount.incrementAndGet();
                      // Alternate between simulated successes and failures
                      if (i % 7 == 0) {
                        breaker.failed();
                      } else {
                        breaker.answered();
                      }
                    } else {
                      rejectedCount.incrementAndGet();
                    }
                  }
                } catch (InterruptedException ex) {
                  Thread.currentThread().interrupt();
                }
              }));
    }

    startLatch.countDown();
    for (var f : futures) {
      f.get(10, TimeUnit.SECONDS);
    }
    executor.shutdown();

    assertThat(allowedCount.get() + rejectedCount.get()).isEqualTo(threads * iterations);
    assertThat(breaker.state())
        .isIn(Breaker.State.CLOSED, Breaker.State.OPEN, Breaker.State.HALF_OPEN);
  }

  @Test
  void configPrototypeInheritanceAllowsEnvironmentAndSchemeOverrides() {
    // Base platform configuration: 5 failures, 30s probe
    var globalBase = BreakerConfig.of(5, Duration.ofSeconds(30));
    assertThat(globalBase.failures()).isEqualTo(5);
    assertThat(globalBase.probeAfter()).isEqualTo(Duration.ofSeconds(30));

    // Fast-path POS card rail override (Humo/Uzcard): 3 failures, 5s probe
    var posCardRail =
        BreakerConfig.from(globalBase).withFailures(3).withProbeAfter(Duration.ofSeconds(5));
    assertThat(posCardRail.failures()).isEqualTo(3);
    assertThat(posCardRail.probeAfter()).isEqualTo(Duration.ofSeconds(5));

    // Batch settlement rail override: 10 failures, 60s probe
    var batchRail =
        BreakerConfig.from(globalBase).withFailures(10).withProbeAfter(Duration.ofSeconds(60));
    assertThat(batchRail.failures()).isEqualTo(10);
    assertThat(batchRail.probeAfter()).isEqualTo(Duration.ofSeconds(60));

    // Prototype instantiation with injected clock
    var liveBreaker = posCardRail.create(clock);
    assertThat(liveBreaker.failures()).isEqualTo(3);
    assertThat(liveBreaker.probeAfter()).isEqualTo(Duration.ofSeconds(5));

    // Defensive immutability constraints
    assertThatThrownBy(() -> BreakerConfig.of(0, Duration.ofSeconds(30)))
        .isInstanceOf(GeneralException.class);
    assertThatThrownBy(() -> BreakerConfig.of(5, Duration.ZERO))
        .isInstanceOf(GeneralException.class);
  }

  @Test
  void failurePredicatesClassifyInfrastructureVersusBusinessExceptions() {
    assertThat(BreakerPredicates.isFailure(new IOException("Connection reset by peer"))).isTrue();
    assertThat(BreakerPredicates.isFailure(new TimeoutException("Gateway timed out"))).isTrue();
    assertThat(BreakerPredicates.isFailure(new ResourceAccessException("Socket timeout"))).isTrue();
    assertThat(
            BreakerPredicates.isFailure(
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)))
        .isTrue();
    assertThat(BreakerPredicates.isFailure(new HttpServerErrorException(HttpStatus.BAD_GATEWAY)))
        .isTrue();
    assertThat(
            BreakerPredicates.isFailure(
                new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)))
        .isTrue();

    // Business declines and client validation must never be classified as infrastructure failure
    assertThat(BreakerPredicates.isFailure(new HttpClientErrorException(HttpStatus.BAD_REQUEST)))
        .isFalse();
    assertThat(
            BreakerPredicates.isFailure(
                new HttpClientErrorException(HttpStatus.UNPROCESSABLE_CONTENT)))
        .isFalse();
    assertThat(BreakerPredicates.isFailure(new HttpClientErrorException(HttpStatus.UNAUTHORIZED)))
        .isFalse();
    assertThat(BreakerPredicates.isFailure(new IllegalArgumentException("Invalid CVV"))).isFalse();

    // Nested exceptions
    assertThat(BreakerPredicates.isFailure(new RuntimeException(new IOException("broken pipe"))))
        .isTrue();
  }

  @Test
  void runExecutesVoidOperationsSafely() {
    var count = new AtomicInteger();
    paymentRailBreaker.run(count::incrementAndGet);
    assertThat(count).hasValue(1);
    assertThat(paymentRailBreaker.isClosed()).isTrue();
  }
}

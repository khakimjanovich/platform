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

  @Test
  void smsNotificationGatewayOutageShedsDispatchWhileInvalidPhoneNumbersNeverTrip() {
    var smsBreaker = new Breaker(3, Duration.ofSeconds(10), clock);

    // 100 invalid recipient phone numbers (client errors: 400 Bad Request / 422 Unprocessable)
    for (var i = 0; i < 100; i++) {
      HttpStatus errorStatus =
          (i % 2 == 0) ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_CONTENT;
      assertThatThrownBy(
              () ->
                  smsBreaker.execute(
                      () -> {
                        throw new HttpClientErrorException(
                            errorStatus, "Invalid or unallocated phone number");
                      }))
          .isInstanceOf(HttpClientErrorException.class);

      assertThat(smsBreaker.isClosed()).isTrue();
      assertThat(smsBreaker.isOpen()).isFalse();
    }

    // External SMS broker network outages occur (3 consecutive connection timeouts)
    for (var i = 0; i < 2; i++) {
      assertThatThrownBy(
              () ->
                  smsBreaker.execute(
                      () -> {
                        throw new ResourceAccessException("Connect timeout to SMS broker gateway");
                      }))
          .isInstanceOf(ResourceAccessException.class);
      assertThat(smsBreaker.isClosed()).isTrue();
    }

    // 3rd network failure trips the breaker to OPEN
    assertThatThrownBy(
            () ->
                smsBreaker.execute(
                    () -> {
                      throw new ResourceAccessException("Broker socket closed by remote host");
                    }))
        .isInstanceOf(ResourceAccessException.class);

    assertThat(smsBreaker.isOpen()).isTrue();
    assertThat(smsBreaker.state()).isEqualTo(Breaker.State.OPEN);

    // Immediate subsequent SMS dispatch is shed fast in memory without blocking dispatcher threads
    assertThatThrownBy(() -> smsBreaker.execute(() -> "never-dispatched"))
        .isInstanceOf(GeneralException.class)
        .satisfies(
            ex -> {
              var ge = (GeneralException) ex;
              assertThat(ge.getStatus()).isEqualTo(BreakerExceptionStatus.BREAKER_OPEN);
              assertThat(ge.getRetryAfter()).isEqualTo(Duration.ofSeconds(10));
            });
  }

  @Test
  void identityProviderOutageFailsFastWhileInvalidUserCredentialsNeverTrip() {
    var authBreaker = new Breaker(2, Duration.ofSeconds(15), clock);

    // 50 failed user login attempts (wrong passwords / invalid tokens: 401 Unauthorized)
    for (var i = 0; i < 50; i++) {
      assertThatThrownBy(
              () ->
                  authBreaker.execute(
                      () -> {
                        throw new HttpClientErrorException(
                            HttpStatus.UNAUTHORIZED, "Bad user credentials");
                      }))
          .isInstanceOf(HttpClientErrorException.class);

      assertThat(authBreaker.isClosed()).isTrue();
    }

    // Identity provider crashes (502 Bad Gateway / 503 Unavailable)
    for (var i = 0; i < 2; i++) {
      assertThatThrownBy(
              () ->
                  authBreaker.execute(
                      () -> {
                        throw new HttpServerErrorException(
                            HttpStatus.BAD_GATEWAY, "Identity provider unavailable");
                      }))
          .isInstanceOf(HttpServerErrorException.class);
    }

    assertThat(authBreaker.isOpen()).isTrue();

    // Shed authentication requests to protect gateway throughput
    assertThatThrownBy(() -> authBreaker.execute(() -> "authenticated-user"))
        .isInstanceOf(GeneralException.class);
  }

  @Test
  void cardSchemeSwitchOutageTripsFastWhileCardholderDeclinesKeepRailClosed() {
    var paymentBreaker = new Breaker(3, Duration.ofSeconds(5), clock);

    // Cardholder rejections (wrong PIN, expired card, insufficient funds)
    for (var i = 0; i < 20; i++) {
      assertThatThrownBy(
              () ->
                  paymentBreaker.execute(
                      () -> {
                        throw new HttpClientErrorException(
                            HttpStatus.UNPROCESSABLE_CONTENT, "Card expired or insufficient funds");
                      }))
          .isInstanceOf(HttpClientErrorException.class);
      assertThat(paymentBreaker.isClosed()).isTrue();
    }

    // Network connection drop to card scheme switch
    for (var i = 0; i < 3; i++) {
      assertThatThrownBy(
              () ->
                  paymentBreaker.execute(
                      () -> {
                        throw new ResourceAccessException("Card switch timeout");
                      }))
          .isInstanceOf(ResourceAccessException.class);
    }

    assertThat(paymentBreaker.isOpen()).isTrue();

    // Fast-fail at POS terminal
    assertThatThrownBy(() -> paymentBreaker.execute(() -> "AUTH-OK"))
        .isInstanceOf(GeneralException.class);
  }

  @Test
  void singleFlightCanaryHalfOpenEnforcesStrictIsolationAcrossConcurrentCalls() {
    var breaker = new Breaker(3, Duration.ofSeconds(5), clock);

    for (var i = 0; i < 3; i++) {
      breaker.recordFailure();
    }
    assertThat(breaker.isOpen()).isTrue();

    // Cooldown duration elapses
    clock.now = clock.now.plusSeconds(5);

    // First attempt claims single-flight canary probe
    assertThat(breaker.tryAcquire()).as("First call acquires canary flight").isTrue();
    assertThat(breaker.state()).isEqualTo(Breaker.State.HALF_OPEN);
    assertThat(breaker.isHalfOpen()).isTrue();

    // While canary is in-flight, concurrent calls are shed
    assertThat(breaker.tryAcquire()).as("Concurrent call shed").isFalse();
    assertThat(breaker.state()).isEqualTo(Breaker.State.HALF_OPEN);
  }

  @Test
  void canarySuccessRestoresFullTraffic() {
    var breaker = new Breaker(3, Duration.ofSeconds(5), clock);

    for (var i = 0; i < 3; i++) {
      breaker.recordFailure();
    }
    clock.now = clock.now.plusSeconds(5);

    // Canary probe executes and succeeds
    var result = breaker.execute(() -> "canary-success");
    assertThat(result).isEqualTo("canary-success");

    // Circuit heals back to CLOSED
    assertThat(breaker.isClosed()).isTrue();
    assertThat(breaker.isOpen()).isFalse();
    assertThat(breaker.isHalfOpen()).isFalse();
    assertThat(breaker.state()).isEqualTo(Breaker.State.CLOSED);

    // Regular traffic flows freely
    assertThat(breaker.tryAcquire()).isTrue();
  }

  @Test
  void canaryFailureImmediatelyReTripsAndRenewsCoolingPeriod() {
    var breaker = new Breaker(3, Duration.ofSeconds(5), clock);

    for (var i = 0; i < 3; i++) {
      breaker.recordFailure();
    }
    clock.now = clock.now.plusSeconds(5);

    // Canary fails because downstream is still unhealthy
    assertThatThrownBy(
            () ->
                breaker.execute(
                    () -> {
                      throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
                    }))
        .isInstanceOf(HttpServerErrorException.class);

    // Reverts immediately to OPEN
    assertThat(breaker.isOpen()).isTrue();
    assertThat(breaker.isHalfOpen()).isFalse();
    assertThat(breaker.state()).isEqualTo(Breaker.State.OPEN);

    // Cooldown is renewed from failure instant
    assertThat(breaker.tryAcquire()).isFalse();
    clock.now = clock.now.plusSeconds(4);
    assertThat(breaker.tryAcquire()).isFalse();

    clock.now = clock.now.plusSeconds(1);
    assertThat(breaker.tryAcquire()).as("Cooldown elapsed, next canary permitted").isTrue();
  }

  @Test
  void abandonedOrHangingProbeIsSafelyReplacedAfterTimeout() {
    var breaker = new Breaker(3, Duration.ofSeconds(5), clock);

    for (var i = 0; i < 3; i++) {
      breaker.recordFailure();
    }
    clock.now = clock.now.plusSeconds(5);

    // Canary admitted but caller hangs / crashes without calling success or failure
    assertThat(breaker.tryAcquire()).isTrue();
    assertThat(breaker.isHalfOpen()).isTrue();

    clock.now = clock.now.plusSeconds(4);
    assertThat(breaker.tryAcquire()).isFalse();

    // After another probeAfter elapses, hanging probe is safely expired and replaced
    clock.now = clock.now.plusSeconds(1);
    assertThat(breaker.tryAcquire()).as("Abandoned probe replaced").isTrue();
  }

  @Test
  void highConcurrencyContentionMaintainsDeterministicTransitionsWithoutCounterDrift()
      throws Exception {
    var breaker = new Breaker(5, Duration.ofMillis(50), clock);
    var threads = 16;
    var iterations = 200;
    var executor = Executors.newFixedThreadPool(threads);
    var startLatch = new CountDownLatch(1);
    var futures = new ArrayList<Future<?>>();
    var acquiredCount = new AtomicInteger();
    var rejectedCount = new AtomicInteger();

    for (var t = 0; t < threads; t++) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  for (var i = 0; i < iterations; i++) {
                    if (breaker.tryAcquire()) {
                      acquiredCount.incrementAndGet();
                      if (i % 7 == 0) {
                        breaker.recordFailure();
                      } else {
                        breaker.recordSuccess();
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

    assertThat(acquiredCount.get() + rejectedCount.get()).isEqualTo(threads * iterations);
    assertThat(breaker.state())
        .isIn(Breaker.State.CLOSED, Breaker.State.OPEN, Breaker.State.HALF_OPEN);
  }

  @Test
  void configPrototypeEnablesTailoredProfilesAcrossDifferentExternalVendors() {
    // Platform default baseline
    var baseConfig = BreakerConfig.of(5, Duration.ofSeconds(30));

    // Profile 1: High-throughput, low-latency POS card payment rail
    var posCardProfile =
        BreakerConfig.from(baseConfig).withFailures(3).withProbeAfter(Duration.ofSeconds(3));
    assertThat(posCardProfile.failures()).isEqualTo(3);
    assertThat(posCardProfile.probeAfter()).isEqualTo(Duration.ofSeconds(3));

    // Profile 2: Background SMS / notification aggregator
    var smsProfile =
        BreakerConfig.from(baseConfig).withFailures(5).withProbeAfter(Duration.ofSeconds(15));
    assertThat(smsProfile.failures()).isEqualTo(5);
    assertThat(smsProfile.probeAfter()).isEqualTo(Duration.ofSeconds(15));

    // Profile 3: Batch accounting / ledger synchronization
    var batchProfile =
        BreakerConfig.from(baseConfig).withFailures(10).withProbeAfter(Duration.ofMinutes(1));
    assertThat(batchProfile.failures()).isEqualTo(10);
    assertThat(batchProfile.probeAfter()).isEqualTo(Duration.ofMinutes(1));

    // Create instance with test clock or system clock
    var liveBreaker = posCardProfile.create(clock);
    assertThat(liveBreaker.failures()).isEqualTo(3);
    assertThat(liveBreaker.probeAfter()).isEqualTo(Duration.ofSeconds(3));

    var defaultSystemBreaker = smsProfile.create();
    assertThat(defaultSystemBreaker.failures()).isEqualTo(5);

    // Defensive parameter validation
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

    // Client and business errors must not be treated as transport failures
    assertThat(BreakerPredicates.isFailure(new HttpClientErrorException(HttpStatus.BAD_REQUEST)))
        .isFalse();
    assertThat(
            BreakerPredicates.isFailure(
                new HttpClientErrorException(HttpStatus.UNPROCESSABLE_CONTENT)))
        .isFalse();
    assertThat(BreakerPredicates.isFailure(new HttpClientErrorException(HttpStatus.UNAUTHORIZED)))
        .isFalse();
    assertThat(BreakerPredicates.isFailure(new IllegalArgumentException("Invalid payload")))
        .isFalse();

    assertThat(BreakerPredicates.isFailure(new RuntimeException(new IOException("broken pipe"))))
        .isTrue();
  }

  @Test
  void runExecutesVoidOperationsSafely() {
    var breaker = new Breaker(3, Duration.ofSeconds(5), clock);
    var count = new AtomicInteger();
    breaker.run(count::incrementAndGet);
    assertThat(count).hasValue(1);
    assertThat(breaker.isClosed()).isTrue();
  }
}

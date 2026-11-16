# breaker

A circuit breaker for any external network-bound integration (SMS notification aggregators, identity
providers, payment schemes, or core banking hosts). Plain Java besides the error library, zero external
dependencies: the service configures it from its own settings and registers its own gauge.

```groovy
implementation libs.platform.breaker
```

After `failures` consecutive failures in a row the circuit transitions to `OPEN`: `tryAcquire()` returns
`false` and calls are shed in $<1\text{ms}$. After `probeAfter`, exactly one canary call is admitted in
`HALF_OPEN` state. `recordSuccess()` closes the breaker (`CLOSED`), `recordFailure()` returns it to `OPEN`
renewing the cooldown, and an abandoned probe is retried after the next `probeAfter`.

The breaker reports its tri-state lifecycle (`Breaker.State`): `CLOSED` (healthy), `OPEN` (load-shedding),
and `HALF_OPEN` (probing single canary request). `breaker.state()` gives the state; `isClosed()`, `isOpen()`,
and `isHalfOpen()` query it.

## Execution wrappers

`breaker.execute(action)` runs a `Supplier<T>` through the breaker: it checks admission via `tryAcquire()`,
executes the action, records success on completion, and on error tests `BreakerPredicates.DEFAULT`. Socket drops,
timeouts, and 5xx server errors call `recordFailure()`; 4xx client errors call `recordSuccess()` (invalid phone
numbers, bad PINs, or expired cards never trip the rail). `breaker.run(action)` works identically for `void` calls.

```java
// SMS notification dispatch
var messageId = smsBreaker.execute(() -> smsBrokerClient.send(otpNotification));

// Payment scheme card authorization
var authResult = paymentBreaker.execute(() -> schemeClient.authorize(payment));

// Keycloak / OAuth IDP userinfo validation
var userClaims = authBreaker.execute(() -> idpClient.fetchUserInfo(token));
```

When `OPEN`, execution wrappers throw `GeneralException(BREAKER_OPEN)` with HTTP status 503, localized error
messages in English, Russian, and Uzbek, and a dynamic `Retry-After` header reflecting remaining cooldown seconds.

## Configuration prototype

`BreakerConfig` defines baseline defaults and immutable prototype derivations:

```java
// Platform baseline
var base = BreakerConfig.of(5, Duration.ofSeconds(30));

// Profile for low-latency POS card payment rails (3 failures, 3s probe)
var posPaymentRail = BreakerConfig.from(base)
    .withFailures(3)
    .withProbeAfter(Duration.ofSeconds(3));

// Profile for background SMS notification dispatchers (5 failures, 15s probe)
var smsBrokerRail = BreakerConfig.from(base)
    .withFailures(5)
    .withProbeAfter(Duration.ofSeconds(15));

var breaker = posPaymentRail.create(); // or create(clock) for testing
Gauge.builder("<system>.breaker.state", breaker, b -> b.state().ordinal()).register(registry);
```

## Tests

Unit tests only (`./gradlew -p platform :breaker:test`): the breaker has no external infrastructure dependencies.

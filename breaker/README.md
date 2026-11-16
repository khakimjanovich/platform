# breaker

A circuit breaker for one outside system (a vendor, an identity provider, a payment agent). Plain
Java besides the error library, no Micrometer: the service builds it from its own settings and
registers its own gauge.

```groovy
implementation libs.platform.breaker
```

After `failures` failures in a row it opens: `allows()` is false and the call does not go out.
After `probeAfter` one call goes through as a probe; `answered()` closes the breaker, `failed()`
opens it again, and a probe never reported is retried after the next `probeAfter`. Report every
call that `allows()` let through, an unexpected exception too. Each instance keeps its own count
in memory; it is not shared between replicas.

The breaker reports its tri-state lifecycle (`Breaker.State`): `CLOSED` (healthy), `OPEN`
(shedding load), and `HALF_OPEN` (probing single canary request). `breaker.state()` gives the
state; `isClosed()`, `isOpen()`, and `isHalfOpen()` query it.

## Execution wrapper

`breaker.execute(action)` runs the supplier through the breaker: it checks `allows()`, runs the action,
calls `answered()` on success, and on error tests `BreakerPredicates.DEFAULT`. Transport drops,
timeouts and 5xx errors call `failed()`; 4xx client errors call `answered()` (bad cards or invalid
inputs do not trip the rail). `breaker.run(action)` works the same for `void` calls.

```java
var result = breaker.execute(() -> schemeClient.authorize(payment));
```

## Configuration prototype

`BreakerConfig` defines baseline settings and prototype overrides:

```java
var base = BreakerConfig.of(5, Duration.ofSeconds(30));
var paymentRail = BreakerConfig.from(base)
    .withFailures(3)
    .withProbeAfter(Duration.ofSeconds(5));

var breaker = paymentRail.create(clock);
Gauge.builder("<system>.breaker.open", breaker, b -> b.isOpen() ? 1 : 0).register(registry);
Gauge.builder("<system>.breaker.state", breaker, b -> b.state().ordinal()).register(registry);
```

No properties of its own, no beans: when open, execution wrappers throw `GeneralException(BREAKER_OPEN)` with a 503 status code and `Retry-After` header. Bad settings (`failures` below 1, `probeAfter` not positive) throw `GeneralException(INTERNAL_ERROR)`.

## Tests

Unit tests only (`./gradlew -p platform :breaker:test`): the breaker has no infrastructure.

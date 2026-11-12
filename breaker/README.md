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
call that `allows()` let through, an unexpected exception too. The caller decides what counts:
usually no answer in time, no connection or a 5xx is `failed()`, and any other answer (a 4xx too)
is `answered()`. Each instance keeps its own count
in memory; it is not shared between replicas.

```java
var breaker = new Breaker(5, Duration.ofSeconds(30), clock); // failures >= 1, probeAfter > 0
Gauge.builder("<system>.breaker.open", breaker, b -> b.isOpen() ? 1 : 0).register(registry);

if (!breaker.allows()) {
  throw new GeneralException(SERVICE_UNAVAILABLE).subError("breaker", "open");
}
```

No properties of its own, no beans, no error codes of its own: bad settings (`failures` below 1,
`probeAfter` not positive) are `GeneralException(INTERNAL_ERROR)`.

## Tests

Unit tests only (`./gradlew -p platform :breaker:test`): the breaker has no infrastructure.

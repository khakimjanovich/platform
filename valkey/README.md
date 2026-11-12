# valkey

Key names and counters in Valkey for any module. Every key is `<service>:<kind>:<id>`: the service
is the app's, and its Valkey ACL user reaches only keys `<service>:*`; the kind and the id are the
module's. A counter lives for its window from its first count, then starts again from zero.

```groovy
implementation libs.platform.valkey // needs Valkey (spring.data.redis.*)
```

```yaml
spring:
  data:
    redis:
      host: ${VALKEY_HOST}
      port: ${VALKEY_PORT}
      username: ${VALKEY_USERNAME} # the ACL user: keys <service>:* only
      password: ${VALKEY_PASSWORD}
platform:
  valkey:
    service: ${VALKEY_SERVICE} # first part of every key, the ACL user's prefix
```

```java
var key = keys.key("message", messageId); // the module's own value: valkey.opsForValue().set(key, …)
keys.hit("code-sends", hashedNumber, 3, Duration.ofHours(1)) // empty: allowed; else Retry-After
    .ifPresent(wait -> { throw new GeneralException(MY_SEND_LIMIT).retryAfter(wait); });
keys.count("checks", ref); // an operator reads a counter
keys.reset("checks", ref); // and clears it
```

The library names no kind, id or limit: the module gives them. An id that is personal data (a phone
number) is the module's to hash first. A kind is lower case words joined by `-`; a window is whole
seconds.

The commands are GET, DEL, INCR, EXPIRE and TTL, so the ACL user needs no script:
`-@all +get +set +del +incr +expire +ttl +ping +info +hello +client|setinfo`. A count whose EXPIRE
was lost (the app stopped between INCR and EXPIRE) gets its expiry on the next count
(`EXPIRE … NX`). A Valkey that can't be reached throws Spring's `DataAccessException`: the module
fails closed with its own error.

## Properties

None has a default: a missing or unworkable value stops startup, naming the property.

| Property | What |
|---|---|
| `platform.valkey.service` | first part of every key, lower case words joined by `-`; the ACL user's prefix |

Where Valkey is and how to sign in are Spring Boot's `spring.data.redis.*`.

## Beans you can replace

`ValkeyKeys`.

## Tests

- `./gradlew -p platform :valkey:test`: key names, refused kinds, ids, windows and limits, and a
  missing or unworkable service that stops startup.
- `./gradlew -p platform :valkey:integrationTest`: a real Valkey with an ACL user like
  heeyid-infra's: counts under the prefix, the window from the first count, a lost expiry healed,
  a limit with its Retry-After and a reset, another prefix refused, and Valkey down.

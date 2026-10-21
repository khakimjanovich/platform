# otp

One-time codes for any module: issued and checked in Redis by one Lua script each, so limits hold
and a code works once even under concurrent requests. Redis keeps only HMAC-SHA256 hashes of the
code and of the subject (an email), keyed with a secret.

```groovy
implementation libs.ecom.otp // needs Redis (spring.data.redis.*)
```

```java
var code = otp.issue("registration", email); // the caller delivers it
otp.verify("registration", email, typedCode); // returns normally if right; never twice
```

Wrong codes count for the whole send window, across codes, so a lockout lasts until the window
ends and a locked subject gets no new code. A new code waits `resend-after` after the last one.
Without Redis nothing is issued or accepted.

## Properties

| Property | Default | What |
|---|---|---|
| `ecom.otp.secret` | — | key of the stored hashes; required, from Vault |
| `ecom.otp.length` | 6 | digits in a code, 4 to 10 |
| `ecom.otp.ttl` | 5m | how long a code stays valid |
| `ecom.otp.max-attempts` | 5 | wrong codes allowed per send window |
| `ecom.otp.max-sends` | 3 | codes sent to one subject per send window |
| `ecom.otp.send-window` | 15m | the window sends and wrong codes count in |
| `ecom.otp.resend-after` | 60s | wait between two codes; `0s` for none |
| `ecom.otp.key-prefix` | `otp` | first part of every Redis key |

## Errors

| Code | Status | When |
|---|---|---|
| `OTP_EXPIRED` | 410 | the code expired, was used, or was never sent |
| `OTP_INVALID` | 400 | the code is wrong |
| `OTP_ATTEMPTS_EXCEEDED` | 429 | too many wrong codes in this window; `Retry-After` |
| `OTP_SEND_LIMIT` | 429 | too many codes sent in this window; `Retry-After` |
| `OTP_RESEND_TOO_EARLY` | 429 | a code was just sent; `Retry-After` |
| `OTP_UNAVAILABLE` | 503 | Redis can't be reached |

## Metrics

`ecom.otp.issued{flow}`, `ecom.otp.verified{flow}`, `ecom.otp.rejected{flow,reason}` in the app's
meter registry.

## Beans you can replace

`OtpService`.

## Tests

- `./gradlew -p platform :otp:test`: codes, how every script answer becomes an error with
  `Retry-After`, Redis down, the property checks.
- `./gradlew -p platform :otp:integrationTest`: a real Redis (`@ServiceConnection`): single use under
  20 concurrent checks, nothing readable stored, lockout that holds across codes, cooldown, send
  limit, Redis down, counters.

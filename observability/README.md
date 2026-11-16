# observability

Logs, traces and metrics the same way in every service: logs with their trace on the console
(readable text, or ECS JSON with `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`) and over OTLP, one trace
per request carried to every call it makes, metrics over OTLP, every response naming its trace in
`X-Request-Id` (a 401 or 403 too), and every outgoing call logged. Every log line, on every sink,
is masked (card numbers, `key=value` secrets): messages and stack traces are masked; exception
attributes are not exported to OTLP (the masked trace travels in the message). Lines logged before
the context starts (the banner, the auto-configuration report, Vault's bootstrap) are not masked.

```groovy
implementation libs.platform.observability
```

Adding the jar is enough: its `observability-defaults.yaml` loads itself below the service's own
configuration (OTLP endpoints from `OTLP_TRACES_ENDPOINT`, `OTLP_LOGS_ENDPOINT`,
`OTLP_METRICS_ENDPOINT`; sampling from `TRACING_SAMPLING`; ECS names for the trace; p95 histograms
for requests and pool waits). Those four variables are required, with no default: a service
without them doesn't start (config, "Required settings").

## Properties

### Logging & Masking (`platform.logging.masking`)

Configured via `application.yaml`:

```yaml
platform:
  logging:
    masking:
      enabled: true                 # master switch (default: true)
      card-pan: true                # masks 13-19 digit card PANs: 400000******1234 (default: true)
      cvv: true                     # masks CVV/CVC codes (default: true)
      bearer-token: true            # masks Authorization / Bearer tokens (default: true)
      phone-number: true            # masks customer phone numbers (default: true)
      pinfl: true                   # masks PINFL/JShShIR numbers (default: true)
      additional-secret-fields:     # extra field names hidden whole (***)
        - embos_name
      additional-token-fields:      # extra field names cut to their last 4 characters (…xxxx)
        - client_token
      additional-card-fields:       # extra field names masked as card numbers
        - custom_pan
      additional-secrets-beside:    # in JSON, field hidden only beside/under one of these names
        code: [pan, otp]
```

| Property | Default | What |
|---|---|---|
| `platform.logging.masking.enabled` | `true` | master switch: when `false`, log masking is bypassed |
| `platform.logging.masking.card-pan` | `true` | masks card PANs (6+*+4 digits) adhering to PCI-DSS 4.0 |
| `platform.logging.masking.cvv` | `true` | masks CVV/CVC verification codes |
| `platform.logging.masking.bearer-token` | `true` | masks Bearer and JWT authorization credentials |
| `platform.logging.masking.phone-number` | `true` | masks customer phone numbers |
| `platform.logging.masking.pinfl` | `true` | masks Uzbek personal identification numbers (PINFL/JShShIR) |
| `platform.logging.masking.additional-secret-fields` | `[]` | field names hidden whole (`***`), beyond defaults |
| `platform.logging.masking.additional-token-fields` | `[]` | field names cut to last 4 characters (`…xxxx`) |
| `platform.logging.masking.additional-card-fields` | `[]` | field names kept to first 6 and last 4 digits |
| `platform.logging.masking.additional-secrets-beside.<field>` | `{}` | in JSON, `<field>` hidden only beside one of these names |

## Client calls

`HttpClientLogging` logs every call of every client built from Boot's `RestClient.Builder` in at
most three lines (`→`, `←` with status and time, `✗`), with the URI and bodies masked and no
headers. Only a JSON, XML or form answer of up to 4 000 bytes is read for the log; anything else
streams through, logged by its size. A call that sends or receives a secret sets the request
attribute `HttpClientLogging.NO_BODIES` and is logged without either body (Keycloak's
`/client-secret` calls do). A client built on its own request factory (a SOAP one) adds the
`HttpClientLogging` bean itself.

## Health as a metric

With the actuator, every health indicator is the gauge `platform.health.up{component}` (1 UP, 0
otherwise), so an alert can see a component go DOWN (the local stack's "Health DOWN").

## Beans you can replace

`Masking` (the log masking), `HttpClientLogging`, `RequestIdFilter`.

## Tests

- `./gradlew -p platform :observability:test`: masked console and ECS lines, extra names, masked
  stack traces (console, ECS, OTLP), masked URIs, bounded client-log buffering.
- `./gradlew -p platform :observability:integrationTest`: Grafana's LGTM stack: one request's trace
  in Tempo and its masked log lines in Loki under the same trace id as `X-Request-Id`.

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

Masking operates with **zero hardcoded defaults**: if no masking fields are configured, logs remain unmasked. When fields are configured, values $\ge 10$ characters are masked preserving the first 6 and last 4 characters with dots (`860030......7890`), and values $< 10$ characters are masked 1:1 with dots (`...`).

Configured via `application.yaml`:

```yaml
platform:
  logging:
    masking:
      mask-char: "."            # masking character (default: .)
      fields:                   # sensitive field names to mask (empty by default)
        - pan
        - card
        - cvv
        - secret
        - access_token
```

#### Configuring via HashiCorp Vault / Environment Variables for Ops

Ops teams can configure or update sensitive field lists dynamically without code changes using HashiCorp Vault KV or environment variables:

**HashiCorp Vault KV**:
```json
{
  "platform.logging.masking.fields": "pan,card,cvv,secret,access_token",
  "platform.logging.masking.mask-char": "."
}
```

**Environment Variables**:
```bash
PLATFORM_LOGGING_MASKING_FIELDS=pan,card,cvv,secret,access_token
PLATFORM_LOGGING_MASKING_MASK_CHAR=.
```

| Property | Default | What |
|---|---|---|
| `platform.logging.masking.mask-char` | `.` | Masking character used for obscured values |
| `platform.logging.masking.fields` | `[]` | Explicit list of sensitive field names to mask across JSON, XML, forms, URIs, and text |

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

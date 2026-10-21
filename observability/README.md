# observability

Logs, traces and metrics the same way in every service: logs with their trace on the console
(readable text, or ECS JSON with `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`) and over OTLP, one trace
per request carried to every call it makes, metrics over OTLP, every response naming its trace in
`X-Request-Id` (a 401 or 403 too), and every outgoing call logged. Every log line, on every sink,
is masked (card numbers, `key=value` secrets): messages and stack traces are masked; exception
attributes are not exported to OTLP (the masked trace travels in the message). Lines logged before
the context starts (the banner, the auto-configuration report, Vault's bootstrap) are not masked.

```groovy
implementation libs.ecom.observability
```

Adding the jar is enough: its `observability-defaults.yaml` loads itself below the service's own
configuration (OTLP endpoints from `OTLP_TRACES_ENDPOINT`, `OTLP_LOGS_ENDPOINT`,
`OTLP_METRICS_ENDPOINT`; sampling from `TRACING_SAMPLING`; ECS names for the trace; p95 histograms
for requests and pool waits).

## Properties

| Property | What |
|---|---|
| `ecom.observability.masking.additional-secret-fields` | field names hidden whole, beyond the defaults |
| `ecom.observability.masking.additional-token-fields` | field names cut to their last 4 characters |
| `ecom.observability.masking.additional-card-fields` | field names kept to their first 6 and last 4 digits |

## Client calls

`HttpClientLogging` logs every call of every client built from Boot's `RestClient.Builder` in at
most three lines (`→`, `←` with status and time, `✗`), with the URI and bodies masked and no
headers. Only a JSON, XML or form answer of up to 4 000 bytes is read for the log; anything else
streams through, logged by its size. A call that sends or receives a secret sets the request
attribute `HttpClientLogging.NO_BODIES` and is logged without either body (Keycloak's
`/client-secret` calls do). A client built on its own request factory (a SOAP one) adds the
`HttpClientLogging` bean itself.

## Beans you can replace

`Masking` (the log masking), `HttpClientLogging`, `RequestIdFilter`.

## Tests

- `./gradlew -p platform :observability:test`: masked console and ECS lines, extra names, masked
  stack traces (console, ECS, OTLP), masked URIs, bounded client-log buffering.
- `./gradlew -p platform :observability:integrationTest`: Grafana's LGTM stack: one request's trace
  in Tempo and its masked log lines in Loki under the same trace id as `X-Request-Id`.

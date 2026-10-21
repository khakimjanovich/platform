# openapi

The API contracts, one project per module or adapter: `<name>-api/src/main/openapi/<name>-v1.yaml`
(OpenAPI 3.0) in, a jar of generated Spring interfaces and models out. Contract first: the spec is
written by hand, the code is generated into `build/` (never into source), and the spec ships in the
jar under `static/openapi`, so Swagger UI shows exactly what was written.

```groovy
implementation libs.ecom.partner.api
```

## What a contract jar holds

- `uz.heeypay.ecom.<name>.api.<Tag>Api`: server interfaces, which the owning module or adapter
  implements as `@RestController`s.
- `uz.heeypay.ecom.<name>.api.client.<Tag>Client`: `@HttpExchange` interfaces (with
  `contract { client = true }`). A module in the same app injects them; anywhere else they become
  an HTTP client through Spring's HTTP service groups (`@ImportHttpServices`,
  `spring.http.serviceclient.<group>.*`). A module calls another only through its `/internal/v1/...`
  operations, tagged `...Internal`.
- `uz.heeypay.ecom.<name>.api.model.*`: the models, with Jackson's shared annotations only (the app
  runs Jackson 3).

Every contract answers errors with the one Problem in `common/problem-v1.yaml` (RFC 9457 plus
`code`, `errors`, `subErrors`, `instance`, `requestId`), referred to by relative path; it is
Spring's `ProblemDetail`, never a generated class.

## The contract plugin

`ecom.contract` (in `build-logic`) does all of the above. Its `contract { }` block:

| Setting | Default | What |
|---|---|---|
| `client` | false | also generate the `@HttpExchange` client interfaces |
| `useResponseEntity` | true | false makes the client methods return the model itself |
| `schemaMappings` | `Problem` → `ProblemDetail` | schemas generated as an existing type instead of a class |

## Checks

On every pull request that changes a contract, the contracts workflow
(`.github/workflows/contracts.yml`) lints all of them (`redocly.yaml`: unique `operationId`s, tags,
`/internal` operations tagged `...Internal`, no contract's own `Problem`) and compares each with the
base branch's (`oasdiff breaking`); a breaking change needs the `breaking-contract` label. Adding or
removing the label re-runs only that workflow, never the build.

## Tests

`./gradlew -p platform/build-logic test` runs the plugin on a fixture contract (Gradle TestKit):
server and client interfaces with distinct names, no Jackson 2 databind on the jar's classpath, no
generated Problem, and the shared file shipped beside the spec. The contracts themselves are
exercised by every module's and adapter's tests.

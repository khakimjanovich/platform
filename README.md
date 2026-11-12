# platform

The libraries every service builds on, in one Gradle build. The app and each
adapter include it (`includeBuild`), so Gradle builds these projects in place of their
`uz.khakimjanovich.platform:*` coordinates; nothing is published to run them.

| Library | What it gives |
|---|---|
| [error](error/README.md) | one error format: RFC 9457 problem details with a stable code, in three languages |
| [config](config/README.md) | every library's own defaults and error definitions, loaded below the app's configuration |
| [masking](masking/README.md) | what must never reach a log, hidden the same way everywhere |
| [observability](observability/README.md) | logs, traces and metrics, masked, with a request id on every response |
| [security](security/README.md) | Keycloak tokens, route rules, public routes, service-to-service tokens |
| [keycloak](keycloak/README.md) | Keycloak's admin API: users, roles, machine clients |
| [otp](otp/README.md) | one-time codes in Redis |
| [persistence](persistence/README.md) | each module's own pool, schema, migrations and entity manager |
| [vault](vault/README.md) | secrets from Vault, Vault as the source of every secret, and values sealed with Vault Transit |
| [valkey](valkey/README.md) | Valkey key names inside the app's ACL prefix, counters and limits with expiry |
| [file-store](file-store/README.md) | files in S3 buckets, typed from their bytes |
| [build-logic](build-logic/README.md) | the build conventions |

`config` is the platform's base layer, not a module an app wires in: error, file-store, keycloak,
observability, otp and persistence each depend on it (`runtimeOnly project(':config')`) and list
their own defaults in `META-INF/spring/uz.khakimjanovich.platform.config.LibraryDefaults.imports`.
Adding one of them brings `config` along, the way a library brings its own dependencies. It holds
no business code and connects no two libraries; the Composition Root rule (an app wires our modules
together) is about the others.

`versioning` publishes the catalog (`gradle/libs.versions.toml`) on each release (§Versions). The API
contracts live in their own repository, [khakimjanovich/contracts](https://github.com/khakimjanovich/contracts),
released as jars and built with this catalog.

## Build

```bash
./gradlew -p platform check   # every library, unit and integration tests (Docker)
./gradlew check               # from the repository root: the platform and the app
```

Unit tests (`src/test`, `*Test`) need nothing; integration tests (`src/integrationTest`, `*IT`) start
real Postgres, Redis, Valkey, Keycloak, Vault, SeaweedFS or Grafana LGTM with Testcontainers, on the same
image tags as the local stack (each library's `src/integrationTest/resources/images.properties`).

## Versions

- One version for the whole platform, `platform` in `gradle/libs.versions.toml` (the catalog
  every build reads), following Semantic Versioning 2.0.
- While 0.x: breaking changes and additions bump the minor, fixes the patch. From 1.0: breaking →
  major, additions → minor, fixes → patch. Breaking means removing or changing a public class,
  method, property or error code.
- A release is the tag `platform-vX.Y.Z` on `main` (it must equal `platform`). The release
  workflow publishes every library (`uz.khakimjanovich.platform:<lib>:X.Y.Z`, with sources and test
  fixtures), the build plugins (`platform.java`, `platform.library`, `platform.module`,
  `platform.integration-test`, at the same version) and the catalog (`uz.khakimjanovich.platform:versioning:X.Y.Z`) to GitHub Packages
  (`https://maven.pkg.github.com/khakimjanovich/platform`). The contracts build pins the catalog;
  the services pin the libraries the same way, or build them from source with `--include-build`.
- Dependabot proposes the catalog's and the workflows' updates weekly, grouped (`.github/dependabot.yml`).

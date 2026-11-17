# platform

The libraries every service builds on, in one Gradle build. The app and each
adapter include it (`includeBuild`), so Gradle builds these projects in place of their
`uz.khakimjanovich.platform:*` coordinates; nothing is published to run them.

| Library | What it gives |
|---|---|
| [error](error/README.md) | one error format: RFC 9457 problem details with a stable code, in three languages |
| [config](config/README.md) | every library's own defaults and error definitions, loaded below the app's configuration |
| [masking](masking/README.md) | what must never reach a log, hidden the same way everywhere |
| [breaker](breaker/README.md) | a circuit breaker for one outside system: open after failures in a row, one probe at a time |
| [outbox](outbox/README.md) | events written in the module's own transaction, published to Kafka at least once, in order per key |
| [idempotency](idempotency/README.md) | the first answer to a client's Idempotency-Key, kept with the work so a retry gets it again |
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

- Every artifact has its own version in `gradle/libs.versions.toml` (the catalog every build reads),
  following Semantic Versioning 2.0: each library `platform-<lib>`, the build plugins `platform-build`,
  and the catalog itself `platform`, the hub, which pins the set of versions checked together.
- While 0.x: breaking changes and additions bump the minor, fixes the patch. From 1.0: breaking →
  major, additions → minor, fixes → patch. Breaking means removing or changing a public class,
  method, property or error code. A library that takes a breaking change from another raises its own
  version too.
- There are two kinds of release, both to GitHub Packages
  (`https://maven.pkg.github.com/khakimjanovich/platform`):

  | | Snapshot | Production |
  |---|---|---|
  | Branch | `develop` | `main` |
  | Versions | `X.Y.Z-SNAPSHOT` | `X.Y.Z` |
  | Workflow | `snapshot.yml`, on every push | `release.yml`, on a merge that changes the catalog |
  | Publishes | every `-SNAPSHOT` artifact, the build plugins and the catalog (`./gradlew publishSnapshots`) | every artifact whose version has no tag yet, the catalog last |
  | Same version again | allowed: the newest build replaces the previous one for consumers | fails: an artifact changed since the tag of its version must raise it |
  | Tags | none | `<lib>-vX.Y.Z`, `build-logic-vX.Y.Z`, `platform-vX.Y.Z` with a GitHub release |

- Into `main`: a pull request fails the `release gate` while any platform version is a `-SNAPSHOT`
  (`.github/scripts/no-snapshots.sh`) or an artifact changed since the tag of its version
  (`.github/scripts/unreleased.sh`, which also prints what the merge will release). The release
  workflow runs both again and publishes nothing if either fails. To release, drop `-SNAPSHOT` from the
  versions that changed; an unchanged library goes back to its last release.
- Locally, `./gradlew publishLocal` puts the same snapshot set in `~/.m2` and overwrites it on each run,
  with no version bump. A build on the platform reads snapshots from Maven local first, then from the
  registry (a GitHub token with `read:packages` as `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties`):

  ```groovy
  // settings.gradle; gradle.properties: platformVersion=2.8.0-SNAPSHOT
  pluginManagement {
      repositories {
          mavenLocal { mavenContent { snapshotsOnly() } }
          maven {
              url = 'https://maven.pkg.github.com/khakimjanovich/platform'
              credentials {
                  username = providers.gradleProperty('gpr.user').getOrElse('')
                  password = providers.gradleProperty('gpr.key').getOrElse('')
              }
              content { includeGroupByRegex 'platform\\..*' } // the build plugins' markers
          }
          mavenCentral()
          gradlePluginPortal()
      }
  }
  dependencyResolutionManagement {
      repositories {
          mavenLocal {
              mavenContent { snapshotsOnly() }
              content { includeGroup 'uz.khakimjanovich.platform' }
          }
          maven {
              url = 'https://maven.pkg.github.com/khakimjanovich/platform'
              credentials {
                  username = providers.gradleProperty('gpr.user').getOrElse('')
                  password = providers.gradleProperty('gpr.key').getOrElse('')
              }
              content { includeGroup 'uz.khakimjanovich.platform' }
          }
          mavenCentral()
      }
      versionCatalogs {
          create('libs') {
              from("uz.khakimjanovich.platform:versioning:${providers.gradleProperty('platformVersion').get()}")
          }
      }
  }
  // build.gradle
  plugins { id 'platform.library' version '2.8.0-SNAPSHOT' }
  ```
- Raise `platform` only when a consumer needs the new set; libraries can release many times in between.
  The contracts build pins the catalog; the services pin the libraries the same way, or build them from
  source with `--include-build`.
- Dependabot proposes the catalog's and the workflows' updates weekly, grouped (`.github/dependabot.yml`).

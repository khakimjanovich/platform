# platform

The libraries every service builds on, in one Gradle build, published to GitHub Packages as
`uz.khakimjanovich.platform:*`. The services and adapters take them from there through
`platform.settings`: this machine's or develop's snapshots, or main's releases (§Building on the platform).

| Library | What it gives |
|---|---|
| [error](https://github.com/khakimjanovich/error4j) (own repository: error4j) | one error format: RFC 9457 problem details with a stable code, in three languages |
| [config](https://github.com/khakimjanovich/config4j) (own repository: config4j) | every library's own defaults and error definitions, loaded below the app's configuration |
| [masking](https://github.com/khakimjanovich/masking4j) (own repository: masking4j) | what must never reach a log, hidden the same way everywhere |
| [breaker](https://github.com/khakimjanovich/breaker4j) (own repository: breaker4j) | a circuit breaker for one outside system: open after failures in a row, one probe at a time |
| [outbox](https://github.com/khakimjanovich/outbox4j) (own repository: outbox4j) | events written in the module's own transaction, published to Kafka at least once, in order per key |
| [idempotency](idempotency/README.md) | the first answer to a client's Idempotency-Key, kept with the work so a retry gets it again |
| [observability](observability/README.md) | logs, traces and metrics, masked, with a request id on every response |
| [security](https://github.com/khakimjanovich/security4j) (own repository: security4j) | Keycloak tokens, route rules, public routes, service-to-service tokens |
| [keycloak](keycloak/README.md) | Keycloak's admin API: users, roles, machine clients |
| [otp](otp/README.md) | one-time codes in Redis |
| [persistence](https://github.com/khakimjanovich/persistence4j) (own repository: persistence4j) | each module's own pool, schema, migrations and entity manager |
| [vault](vault/README.md) | secrets from Vault, Vault as the source of every secret, and values sealed with Vault Transit |
| [valkey](https://github.com/khakimjanovich/valkey4j) (own repository: valkey4j) | Valkey key names inside the app's ACL prefix, counters and limits with expiry |
| [file-store](file-store/README.md) | files in S3 buckets, typed from their bytes |
| [build-logic](build-logic/README.md) | the build conventions |

`config` is the platform's base layer, not a module an app wires in: error, file-store, keycloak,
observability, otp and persistence each depend on it (`runtimeOnly libs.platform.config`) and list
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
  with no version bump.

- Raise `platform` only when a consumer needs the new set; libraries can release many times in between.
  The contracts build pins the catalog; the services pin the libraries the same way, or build them from
  source with `--include-build`.
- Dependabot proposes the catalog's and the workflows' updates weekly, grouped (`.github/dependabot.yml`).

## Building on the platform

A service, adapter or other repository takes the platform through one settings plugin,
`platform.settings` (build-logic's `settings-plugin`), from one of three sources:

| `platformSource` | Takes the platform from | Version | Use |
|---|---|---|---|
| `local` | `~/.m2` (`./gradlew publishLocal` here), releases from the registry | `platformVersion` + `-SNAPSHOT` | trying platform changes before pushing them |
| `snapshot` | the registry: develop's snapshots | `platformVersion` + `-SNAPSHOT` | develop, the dev server |
| `release` | the registry: main's releases only | `platformVersion` | main, production |

```groovy
// settings.gradle
pluginManagement {
    repositories {
        maven {
            url = 'https://maven.pkg.github.com/khakimjanovich/platform'
            credentials {
                username = providers.environmentVariable('GH_PACKAGES_ACTOR').getOrElse('')
                password = providers.environmentVariable('GH_PACKAGES_TOKEN').getOrElse('')
            }
            content { includeGroupByRegex 'platform\\..*|uz\\.khakimjanovich\\.platform' }
        }
        gradlePluginPortal()
    }
}
plugins { id 'platform.settings' version '2.8.0' }
```

```properties
# gradle.properties: the platform release, without -SNAPSHOT, and where to take it from.
# CI overrides the source per branch: PLATFORM_SOURCE=release on main.
platformVersion=2.8.0
platformSource=snapshot
```

```groovy
// build.gradle: the convention plugins come from the catalog, at no version of the build's own
plugins { alias(libs.plugins.platform.module) }
dependencies { implementation libs.platform.error }
```

What the plugin does:

- The platform's artifacts come only from its repositories (`exclusiveContent`): a `mavenLocal()` that an
  init script adds can't hand a snapshot build a stale local copy. Settings repositories win over a
  project's (`PREFER_SETTINGS`); Maven Central is added for everything else.
- The catalog is imported as `libs`; its `[plugins]` pin the convention plugins at `platform-build`.
- `snapshot`: Gradle caches a `-SNAPSHOT` for 24 hours, and what settings resolve (the catalog, the
  plugins) can't be told otherwise. So the plugin reads the registry's `maven-metadata.xml` on every build
  and pins the newest build's unique version (`2.8.0-20261117.152915-1`); the libraries are checked on
  every resolution (`cacheChangingModulesFor 0`). A clean build therefore always runs on what develop last
  published. If the registry can't be read (offline, `--offline`), Gradle's cached snapshot is used, with a
  warning.
- `release` refuses a `platformVersion` with `-SNAPSHOT`; so do `local` and `snapshot`, which add it.
- Credentials: `GH_PACKAGES_ACTOR` / `GH_PACKAGES_TOKEN`, or `gpr.user` / `gpr.key` in
  `~/.gradle/gradle.properties` (a token with `read:packages`).

Until `platform.settings` 2.8.0 is released, take it at `2.8.0-SNAPSHOT` (from the registry, or with
`mavenLocal()` in `pluginManagement.repositories` after `./gradlew publishLocal`).

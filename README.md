# platform

The hub every `<lib>4j` library and service builds on: one version catalog and the Gradle plugins,
released together at one version and published to GitHub Packages.

| Artifact | Folder | What it is |
|---|---|---|
| `uz.khakimjanovich:version-catalog` | [version-catalog](version-catalog/build.gradle) | `gradle/libs.versions.toml`: every dependency version, the libraries' releases, the plugins' aliases |
| `uz.khakimjanovich:gradle-conventions` | [gradle-plugins/conventions](gradle-plugins/README.md) | `uz.khakimjanovich.java`, `.library`, `.module`, `.integration-test` |
| `uz.khakimjanovich:gradle-settings` | [gradle-plugins/settings](gradle-plugins/README.md) | `uz.khakimjanovich.settings`: where a build takes the hub from (§Building on the platform) |

The libraries live in their own repositories and release on their own; the catalog pins their releases:
[config4j](https://github.com/khakimjanovich/config4j), [masking4j](https://github.com/khakimjanovich/masking4j),
[error4j](https://github.com/khakimjanovich/error4j), [persistence4j](https://github.com/khakimjanovich/persistence4j),
[breaker4j](https://github.com/khakimjanovich/breaker4j), [valkey4j](https://github.com/khakimjanovich/valkey4j),
[vault4j](https://github.com/khakimjanovich/vault4j), [filestore4j](https://github.com/khakimjanovich/filestore4j),
[outbox4j](https://github.com/khakimjanovich/outbox4j), [idempotency4j](https://github.com/khakimjanovich/idempotency4j),
[observability4j](https://github.com/khakimjanovich/observability4j), [keycloak-admin4j](https://github.com/khakimjanovich/keycloak-admin4j),
[security4j](https://github.com/khakimjanovich/security4j), [otp4j](https://github.com/khakimjanovich/otp4j).
Each builds with [library.yml](.github/workflows/library.yml), the reusable workflow here.

## Build

```bash
./gradlew check                 # the plugins' tests, on real builds (TestKit)
./gradlew publishToMavenLocal   # the three artifacts in ~/.m2, for platformSource=local
```

## Versions

- One version for the hub: `platform` in `gradle/libs.versions.toml`, following Semantic Versioning
  2.0. Breaking means removing or changing a catalog alias, a plugin id or what a plugin enforces.
- `develop` carries `X.Y.Z-SNAPSHOT`, republished on every push (`snapshot.yml`) until `X.Y.Z` is
  released; no other snapshot line starts before that.
- `main` carries releases only. A merge publishes `X.Y.Z` once, tags it `vX.Y.Z` and makes a GitHub
  release (`release.yml`). Into `main`, the `release gate` fails while any catalog version is a
  `-SNAPSHOT` (`.github/scripts/no-snapshots.sh`) or the hub changed since the tag of its version
  (`.github/scripts/release-plan.sh`).
- Dependabot proposes the catalog's and the workflows' updates weekly, grouped (`.github/dependabot.yml`).

## Building on the platform

A service, adapter or other repository takes the platform through one settings plugin,
`uz.khakimjanovich.settings` (`gradle-plugins/settings`), from one of three sources:

| `platformSource` | Takes the platform from | Version | Use |
|---|---|---|---|
| `local` | `~/.m2` (`./gradlew publishToMavenLocal` here), releases from the registry | `platformVersion` + `-SNAPSHOT` | trying platform changes before pushing them |
| `snapshot` | the registry: develop's snapshots | `platformVersion` + `-SNAPSHOT` | develop, the dev server |
| `release` | the registry: main's releases only | `platformVersion` | main, production |

```groovy
// settings.gradle
pluginManagement {
    repositories {
        maven {
            url = 'https://maven.pkg.github.com/khakimjanovich/*'
            credentials {
                username = providers.environmentVariable('GH_PACKAGES_ACTOR').getOrElse('')
                password = providers.environmentVariable('GH_PACKAGES_TOKEN').getOrElse('')
            }
            content { includeGroupByRegex 'uz\\.khakimjanovich(\\..+)?' }
        }
        gradlePluginPortal()
    }
}
plugins { id 'uz.khakimjanovich.settings' version '1.0.0' }
```

```properties
# gradle.properties: the platform release, without -SNAPSHOT, and where to take it from.
# CI overrides the source per branch: PLATFORM_SOURCE=release on main.
platformVersion=1.0.0
platformSource=snapshot
```

```groovy
// build.gradle: the convention plugins come from the catalog, at no version of the build's own
plugins { alias(libs.plugins.conventions.module) }
dependencies { implementation libs.error4j }
```

What the plugin does:

- The platform's artifacts come only from its repositories (`exclusiveContent`): a `mavenLocal()` that an
  init script adds can't hand a snapshot build a stale local copy. Settings repositories win over a
  project's (`PREFER_SETTINGS`); Maven Central is added for everything else.
- The catalog is imported as `libs`; its `[plugins]` pin the convention plugins at the hub version.
- `snapshot`: Gradle caches a `-SNAPSHOT` for 24 hours, and what settings resolve (the catalog, the
  plugins) can't be told otherwise. So the plugin reads the registry's `maven-metadata.xml` on every build
  and pins the newest build's unique version (`1.0.0-20261117.152915-1`); the libraries are checked on
  every resolution (`cacheChangingModulesFor 0`). A clean build therefore always runs on what develop last
  published. If the registry can't be read (offline, `--offline`), Gradle's cached snapshot is used, with a
  warning.
- `release` refuses a `platformVersion` with `-SNAPSHOT`; so do `local` and `snapshot`, which add it.
- `PLATFORM_SOURCE` and the other variables win over `gradle.properties`, so CI can switch the source.
- Credentials: `GH_PACKAGES_ACTOR` / `GH_PACKAGES_TOKEN`, or `gpr.user` / `gpr.key` in
  `~/.gradle/gradle.properties` (a token with `read:packages`).


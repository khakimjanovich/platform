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

The model follows Laravel's: release branches, tags, one version for the whole set.

- One version for the hub and all 14 libraries: `platform` in `gradle/libs.versions.toml`, following
  Semantic Versioning 2.0. Release `1.2.0` is the tag `v1.2.0` in all 15 repositories.
- Branches: `1.x` holds the current major and is the default branch; `main` holds the next major.
  Once a year, `main` becomes the next `X.x` and releases `X.0.0`.
- On a branch the version is the line, `1.x-SNAPSHOT`. It is never published; it exists for local
  builds (`./gradlew publishToMavenLocal`, `platformSource=local`).
- A push or a pull request runs `check` only (`build.yml`). Nothing is published from a branch.
- A tag `vX.Y.Z` on `X.x` publishes `X.Y.Z` once, with a GitHub release (`release.yml`,
  `.github/scripts/release-version.sh`). The published catalog must hold no `-SNAPSHOT`.
- A release builds on releases only: the hub first, then the libraries in their dependency order.
- Dependabot proposes the catalog's and the workflows' updates weekly, grouped (`.github/dependabot.yml`).

## Building on the platform

A library, service or adapter takes the platform through one settings plugin,
`uz.khakimjanovich.settings` (`gradle-plugins/settings`), from one of two sources:

| `platformSource` | Takes the platform from | Version | Use |
|---|---|---|---|
| `release` | the registry, releases only | `platformVersion` (`1.0.0`) | CI, every release, production |
| `local` | `~/.m2` (`./gradlew publishToMavenLocal` here and in the libraries) | the line (`1.x-SNAPSHOT`) | trying unreleased changes on this machine |

```groovy
// settings.gradle
pluginManagement {
    def platformVersion = providers.gradleProperty('platformVersion').get()
    def platformSource = providers.environmentVariable('PLATFORM_SOURCE').orElse(providers.gradleProperty('platformSource')).get()
    repositories {
        if (platformSource == 'local') mavenLocal()
        maven {
            url = 'https://maven.pkg.github.com/khakimjanovich/*'
            credentials {
                username = providers.gradleProperty('gpr.user').orElse(providers.environmentVariable('GH_PACKAGES_ACTOR')).getOrElse('')
                password = providers.gradleProperty('gpr.key').orElse(providers.environmentVariable('GH_PACKAGES_TOKEN')).getOrElse('')
            }
            content { includeGroupByRegex 'uz\\.khakimjanovich(\\..+)?' }
        }
        gradlePluginPortal()
    }
    plugins { id 'uz.khakimjanovich.settings' version(platformSource == 'local' ? "${platformVersion.tokenize('.')[0]}.x-SNAPSHOT" : platformVersion) }
}
plugins { id 'uz.khakimjanovich.settings' }
```

```properties
# gradle.properties: the hub release to build on, and where to take it from.
platformVersion=1.0.0
platformSource=release
```

```groovy
// build.gradle: the convention plugins come from the catalog, at no version of the build's own
plugins { alias(libs.plugins.conventions.module) }
dependencies { implementation libs.error4j }
```

What the plugin does:

- The platform's artifacts come only from its repositories (`exclusiveContent`): a `mavenLocal()` that an
  init script adds can't stand in for a release, and the registry hands out releases only. Settings repositories win over a
  project's (`PREFER_SETTINGS`); Maven Central is added for everything else.
- The catalog is imported as `libs`; its `[plugins]` pin the convention plugins at the hub version.
- `platformVersion` must be a release, `X.Y.Z`; `local` takes its line, `X.x-SNAPSHOT`.
- `PLATFORM_SOURCE` and the other variables win over `gradle.properties`, so a release build can force `release`.
- Credentials: `GH_PACKAGES_ACTOR` / `GH_PACKAGES_TOKEN`, or `gpr.user` / `gpr.key` in
  `~/.gradle/gradle.properties` (a token with `read:packages`).


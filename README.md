# gradle

The Gradle tooling of org4j: one version catalog and the Gradle plugins that every org4j library and
every service builds with, released on its own and published to GitHub Packages.

| Artifact | Folder | What it is |
|---|---|---|
| `io.github.org4j:version-catalog` | [version-catalog](version-catalog/build.gradle) | `gradle/libs.versions.toml`: every dependency version, the libraries' releases, the plugins' aliases |
| `io.github.org4j:gradle-conventions` | [gradle-plugins/conventions](gradle-plugins/README.md) | `io.github.org4j.java`, `.library`, `.module`, `.integration-test` |
| `io.github.org4j:gradle-settings` | [gradle-plugins/settings](gradle-plugins/README.md) | `io.github.org4j.settings`: where a build takes the hub from (§Building on org4j) |

The libraries live in their own repositories; the catalog pins their releases:
[config](https://github.com/org4j/config), [masking](https://github.com/org4j/masking),
[error](https://github.com/org4j/error), [persistence](https://github.com/org4j/persistence),
[breaker](https://github.com/org4j/breaker), [valkey](https://github.com/org4j/valkey),
[vault](https://github.com/org4j/vault), [filestore](https://github.com/org4j/filestore),
[outbox](https://github.com/org4j/outbox), [idempotency](https://github.com/org4j/idempotency),
[observability](https://github.com/org4j/observability), [keycloak-admin](https://github.com/org4j/keycloak-admin),
[security](https://github.com/org4j/security), [otp](https://github.com/org4j/otp).
Each builds with [library.yml](.github/workflows/library.yml), the reusable workflow here.

## Build

```bash
./gradlew check                 # the plugins' tests, on real builds (TestKit)
./gradlew publishToMavenLocal   # the three artifacts in ~/.m2, for org4jSource=local
```

## Versions

Release branches and tags, with one major for the whole set and each repository released on its own.

- The major is shared and set by hand: `1.x` is one line for the hub and all 14 libraries.
  Branches: `1.x` holds the current major and is the default branch; `main` holds the next major.
  Once a year, `main` becomes the next `X.x` and releases `X.0.0`.
- A line takes fixes and additive changes (Semantic Versioning 2.0, minor and patch). Anything that
  removes or changes what users rely on waits for the next major.
- Each repository has its own version and releases only when it changed: a tag `vX.Y.Z` on `X.x`
  publishes `X.Y.Z` once, with a GitHub release (`release.yml` for the hub, `library.yml` for a
  library; `.github/scripts/release-version.sh` refuses a tag off its line).
- The hub's version is `org4j`: its catalog and plugins. The catalog names every library by its
  major only (`libraries = "1.+"`), so a service resolves the newest 1.x of each without a new hub,
  and keeps its build exact with Gradle's dependency locking. The hub releases when the hub itself
  changes, not when a library does.
- A library builds and releases on the hub release in its `gradle.properties` (`org4jVersion`), and
  publishes the versions of the other org4j libraries it was built on, so a user gets at least those.
- On a branch every version is the line, `1.x-SNAPSHOT`. It is never published; it exists for local
  builds (`./gradlew publishToMavenLocal`, `org4jSource=local`), where the catalog pins every
  library to the line instead of `1.+`.
- A push or a pull request runs `check` only. Nothing is published from a branch. The published
  catalog must hold no `-SNAPSHOT`.
- A change across libraries releases in dependency order: the library that adds an API first, then
  the one that uses it, once its pull request builds on that release.
- `scripts/release-train.sh X.Y.Z` tags all 15 at one version, wave by wave: for starting a major
  (`X.0.0`), not for everyday releases.
- Dependabot proposes the catalog's and the workflows' updates weekly, grouped (`.github/dependabot.yml`).

## Building on org4j

A library, service or adapter takes org4j through one settings plugin,
`io.github.org4j.settings` (`gradle-plugins/settings`), from one of two sources:

| `org4jSource` | Takes org4j from | Version | Use |
|---|---|---|---|
| `release` | the registry, releases only | `org4jVersion` (`1.0.0`) | CI, every release, production |
| `local` | `~/.m2` (`./gradlew publishToMavenLocal` here and in the libraries) | the line (`1.x-SNAPSHOT`) | trying unreleased changes on this machine |

```groovy
// settings.gradle
pluginManagement {
    def org4jVersion = providers.gradleProperty('org4jVersion').get()
    def org4jSource = providers.environmentVariable('ORG4J_SOURCE').orElse(providers.gradleProperty('org4jSource')).get()
    repositories {
        if (org4jSource == 'local') mavenLocal()
        maven {
            url = 'https://maven.pkg.github.com/org4j/*'
            credentials {
                username = providers.gradleProperty('gpr.user').orElse(providers.environmentVariable('GH_PACKAGES_ACTOR')).getOrElse('')
                password = providers.gradleProperty('gpr.key').orElse(providers.environmentVariable('GH_PACKAGES_TOKEN')).getOrElse('')
            }
            content { includeGroupByRegex 'io\\.github\\.org4j(\\..+)?' }
        }
        gradlePluginPortal()
    }
    plugins { id 'io.github.org4j.settings' version(org4jSource == 'local' ? "${org4jVersion.tokenize('.')[0]}.x-SNAPSHOT" : org4jVersion) }
}
plugins { id 'io.github.org4j.settings' }
```

```properties
# gradle.properties: the hub release to build on, and where to take it from.
org4jVersion=1.0.0
org4jSource=release
```

```groovy
// build.gradle: the convention plugins come from the catalog, at no version of the build's own
plugins { alias(libs.plugins.conventions.module) }
dependencies { implementation libs.error }
```

What the plugin does:

- org4j's artifacts come only from its repositories (`exclusiveContent`): a `mavenLocal()` that an
  init script adds can't stand in for a release, and the registry hands out releases only. Settings repositories win over a
  project's (`PREFER_SETTINGS`); Maven Central is added for everything else.
- The catalog is imported as `libs`; its `[plugins]` pin the convention plugins at the hub version.
- `org4jVersion` must be a release, `X.Y.Z`; `local` takes its line, `X.x-SNAPSHOT`.
- `ORG4J_SOURCE` and the other variables win over `gradle.properties`, so a release build can force `release`.
- Credentials: `GH_PACKAGES_ACTOR` / `GH_PACKAGES_TOKEN`, or `gpr.user` / `gpr.key` in
  `~/.gradle/gradle.properties` (a token with `read:packages`).


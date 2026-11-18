# Plugin map

Where every platform library stands today, what ties it to the others, and the order to move each into
its own repository. The goal: each library releases on its own CI; `platform` becomes the hub that pins
a tested set of versions and releases only when that set changes.

Snapshot: platform 2.7.0, branch `feat/platform-archunit-suite` (`9febde8`).

## 1. Dependency graph (what a consumer gets at runtime)

```
                 ┌──────── build-logic (convention plugins) ── used by every library below
                 │         versioning  (published version catalog)
                 │
L0  config ◄─────┼── runtime loader for every *LibraryDefaults.imports file
L0  masking
L0  outbox                         (no platform dependency)
L0  security                       (no platform dependency at runtime)
L0  persistence ── config (runtime)
L1  error ── masking, config
L2  breaker ── error
L2  valkey ── error
L2  keycloak ── error
L2  file-store ── error
L2  idempotency ── error
L2  vault ── error, masking
L2  observability ── masking, config   (··· breaker, compileOnly / optional)
L3  otp ── error, valkey
```

## 2. Library cards

Main = compile/runtime dependency a consumer inherits. Test = only the library's own tests need it.

| Library | Main deps | Test-only deps | Shares with others | Plugins |
|---|---|---|---|---|
| config | — | — | `LibraryDefaults` loader (spring.factories) | library |
| masking | — | — | used by error, vault, observability | library |
| outbox | — | persistence (IT) | Liquibase `platform-outbox.yaml` | library, integration-test |
| security | — | error (IT), keycloak test fixtures (IT) | — | library, integration-test |
| persistence | config (runtime) | — | — | library, integration-test |
| error | masking, config (runtime) | — | **test fixtures**: `ErrorDefinitions` | library, integration-test, java-test-fixtures |
| breaker | error, config (runtime) | error fixtures | — | library |
| valkey | error, config (runtime) | error fixtures | used by otp | library, integration-test |
| keycloak | error, config (runtime) | observability (IT) | **test fixtures**: `demo-realm.json` | library, integration-test, java-test-fixtures |
| file-store | error, config (runtime) | — | — | library, integration-test |
| idempotency | error, config (runtime) | persistence (IT) | Liquibase `platform-idempotency.yaml` | library, integration-test |
| vault | error, masking, config (runtime) | error fixtures | — | library, integration-test |
| observability | masking, config (runtime); breaker compileOnly | breaker | — | library, integration-test |
| otp | error, valkey, config (runtime) | error fixtures | — | library, integration-test |

Verified against `import` statements in each source set: no library imports another it doesn't declare.

## 3. Coupling that blocks a split today

1. **One version for everything.** *(resolved in wave 0: `platform-<name>` per artifact)* Every library and the plugins take `libs.versions.platform`
   (`platform.library.gradle`, `build-logic/build.gradle`). Every catalog entry
   `platform-*` uses `version.ref = "platform"`. → Each library needs its own version line.
2. **One release for everything.** *(resolved in wave 0: `unreleased.sh` + per-artifact tags)* `release.yml` runs on a change to `libs.versions.toml` and publishes
   all libraries plus build-logic under one `platform-v*` tag.
3. **Plugins read the consumer's catalog.** *(wave 0: still true, now checked up front with a clear error)* `platform.java` and `platform.library` look up
   `rootProject…named('libs')` and expect aliases such as `spring-boot-bom`, `lombok`, `jspecify`,
   `errorprone-core`, `nullaway`, `google-java-format`. Every new repo must import the published
   `versioning` catalog as `libs`, or the plugins must stop depending on aliases.
4. **build-logic knows the libraries by name.** `verifyPlatformFirst` maps Redis, Vault, Keycloak,
   Hikari and Liquibase to `libs.platform.valkey|vault|keycloak|persistence`. That's only a message string,
   but it ties the plugin's release to the library names.
5. **Shared test fixtures.** `error` ships `ErrorDefinitions` (used by breaker, otp, valkey, vault).
   `keycloak` ships `demo-realm.json` (used by security). After the split these are published
   artifacts with versions. A fixture change in error then needs a release before the others can
   test against it.
6. **A string contract with config.** Libraries register defaults in
   `META-INF/spring/uz.khakimjanovich.platform.config.LibraryDefaults.imports`. The file name is
   config's class name. Renaming or moving that class silently disables every library's defaults.
   This is config's public API and must stay frozen, or be tested in each library.
7. **Optional compile against breaker.** observability compiles against breaker without shipping it.
   In separate repos the two can drift. Pin a range, or add a contract test.
8. **Monorepo CI.** The root `build.gradle` aggregates `check` over all subprojects. `build.yml` runs one
   `./gradlew check`.
9. **Registry.** Everything publishes to `maven.pkg.github.com/khakimjanovich/platform`. Decide whether
   each repo publishes to its own package URL, or all of them to one, before the first extraction.

## 4. Target shape

```
platform-build   (repo)  build-logic plugins           own version, own CI
platform         (repo)  hub: version catalog / BOM    releases only when a pinned set changes
platform-config  (repo)  ┐
platform-masking (repo)  │ each: own version, own CI, depends on published
platform-error   (repo)  │ platform-build plugins and published upstream libraries
…                        ┘
```

The hub's catalog pins `platform-build` and every library at the versions verified together. Apps and
adapters keep importing only the hub catalog. A library can ship many releases; the hub moves only when
a consumer needs the new version.

## 5. Extraction order (leaves of the plugin graph first, consumers last)

| Wave | Move out | Needs first |
|---|---|---|
| 0 | build-logic → `platform-build` | ~~per-artifact versions (#1)~~, ~~release per artifact (#2)~~, ~~alias contract checked (#3)~~: done on `chore/per-library-versions`; baseline tags `<name>-v2.7.0` before it merges |
| 1 | config, masking | **done**: [config4j](https://github.com/khakimjanovich/config4j), [masking4j](https://github.com/khakimjanovich/masking4j); the hub keeps catalog, build-logic and platform.settings (wave 0 stays in the hub) |
| 2 | outbox, persistence, security | **done**: [persistence4j](https://github.com/khakimjanovich/persistence4j), [outbox4j](https://github.com/khakimjanovich/outbox4j), [security4j](https://github.com/khakimjanovich/security4j) |
| 3 | error | **done**: [error4j](https://github.com/khakimjanovich/error4j) |
| 4 | breaker ([breaker4j](https://github.com/khakimjanovich/breaker4j) done), valkey ([valkey4j](https://github.com/khakimjanovich/valkey4j) done), vault, keycloak, file-store, idempotency, observability | error published; observability↔breaker contract (#7) |
| 5 | otp | valkey published |
| 6 | `platform` reduced to hub | all libraries out; `release.yml` reworked (#2) |

In every wave the monorepo keeps building against the published artifact of the extracted library.
Nothing is deleted here until the new repo has released at least once.

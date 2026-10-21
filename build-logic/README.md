# build-logic

The conventions every ecom build applies, as Gradle convention plugins, so a module's or library's
build file says only what is particular to it.

| Plugin | For | What it gives |
|---|---|---|
| `ecom.java` | every Java project | Java from the catalog, the Spring Boot BOM, Lombok, JUnit, `-parameters`, google-java-format checked by `check`, JSpecify and NullAway (a warning) on `@NullMarked` code |
| `ecom.library` | platform libraries | `ecom.java`, the platform's group and version, sources, configuration metadata, NullAway as an error |
| `ecom.module` | the app and its modules | `ecom.java`, the Spring Modulith BOM, integration tests |
| `ecom.integration-test` | anything with integration tests | `src/integrationTest` (`*IT`) as a JVM test suite, run by `check` |
| `ecom.contract` | `openapi/*-api` | see `../openapi/README.md` |

Versions come from `../gradle/libs.versions.toml`, the one catalog.

## Tests

`./gradlew -p platform/build-logic test` (part of the platform's `check`) runs the contract plugin
on a fixture build with Gradle TestKit.

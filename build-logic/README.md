# build-logic

The conventions every ecom build applies, as Gradle convention plugins, so a module's or library's
build file says only what is particular to it.

| Plugin | For | What it gives |
|---|---|---|
| `platform.java` | every Java project | Java from the catalog, the Spring Boot BOM, Lombok, JUnit, `-parameters`, google-java-format checked by `check`, JSpecify and NullAway (a warning) on `@NullMarked` code |
| `platform.library` | platform libraries | `platform.java`, the platform's group and version, sources, configuration metadata, NullAway as an error |
| `platform.module` | the app and its modules | `platform.java`, the Spring Modulith BOM, integration tests |
| `platform.integration-test` | anything with integration tests | `src/integrationTest` (`*IT`) as a JVM test suite, run by `check` |

Versions come from `../gradle/libs.versions.toml`, the one catalog. The contract plugin moved with
the contracts to [khakimjanovich/contracts](https://github.com/khakimjanovich/contracts).

# build-logic

The conventions every build on the platform applies, as Gradle convention plugins, so a module's or library's
build file says only what is particular to it.

| Plugin | For | What it gives |
|---|---|---|
| `platform.java` | every Java project | Java from the catalog, the Spring Boot BOM, Lombok, JUnit, `-parameters`, google-java-format checked by `check`, every javac warning an error (`-Xlint:all -Werror`), Error Prone's default checks, JSpecify and NullAway (an error), every `src/main` package `@NullMarked` (`verifyNullMarked`), no workaround markers (`verifyNoWorkarounds`), one architecture (`verifyArchitecture`, see below) |
| `platform.library` | platform libraries | `platform.java`, the platform's group and version, sources, configuration metadata |
| `platform.module` | the app and its modules | `platform.java`, the Spring Modulith BOM, integration tests |
| `platform.integration-test` | anything with integration tests | `src/integrationTest` (`*IT`) as a JVM test suite, run by `check` |

Versions come from `../gradle/libs.versions.toml`, the one catalog. The contract plugin moved with
the contracts to [khakimjanovich/contracts](https://github.com/khakimjanovich/contracts).

## No workarounds

A problem in a repository we own (platform, contracts, the services) is fixed there and released; one
outside (a library's API, a scheme's quirk) is isolated in one place with a comment naming it. `check`
fails on what marks a workaround instead (`verifyNoWorkarounds`, in `platform.java`):

- in `src/main` and the build file: a comment saying `TODO`, `FIXME`, `HACK`, `XXX`, "workaround",
  "for now" or "temporary fix" (and the like: "temporary token" is a domain word, not a marker);
  `ignoreFailures` or `-x test`;
- in `src/main`: a `@SuppressWarnings` without a `//` reason on the line above;
- in any source set: a `@Disabled` test.

A `ponytail:` comment marks a deliberate simplification with its limit and upgrade path, listed in the
debt ledger; it is allowed. An open question goes to the backlog, not into the code.

## One architecture

`check` runs `verifyArchitecture` (in `platform.java`) on the classes compiled from `src/main/java`;
generated code is left out. The rules are in `ArchitectureRules`, the same for every service:

- a controller calls services: never a repository, a service implementation, an entity, a mapper,
  a workflow or configuration;
- only a service implementation, a workflow or configuration reaches a repository;
- nothing calls a controller, and a controller is never its contract's client;
- a constant (a static final field of a value type: a primitive, a string, a number, a `Duration`, a
  `Pattern`, a `DateTimeFormatter`, a `Set`, `List` or `Map`) lives in a `ConstantUtils` or
  `*Constants` class, and such a class holds only static final fields. An enum's values,
  `serialVersionUID` and objects that are not values (a logger, a mapper) are not constants.

A project without a layer (a library with no controller) passes the rules about it.

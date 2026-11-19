# gradle-plugins

The conventions every build on org4j applies, as Gradle convention plugins, so a module's or library's
build file says only what is particular to it.

| Plugin | For | What it gives |
|---|---|---|
| `io.github.org4j.java` | every Java project | Java from the catalog, the Spring Boot BOM, Lombok, JUnit, `-parameters`, google-java-format checked by `check`, every javac warning an error (`-Xlint:all -Werror`), Error Prone's default checks, JSpecify and NullAway (an error), every `src/main` package `@NullMarked` (`verifyNullMarked`), no workaround markers (`verifyNoWorkarounds`), one architecture (`verifyArchitecture`, see below), platform first (`verifyOrg4jFirst`, see below) |
| `io.github.org4j.library` | org4j libraries | `io.github.org4j.java`, org4j's group and version, sources, configuration metadata |
| `io.github.org4j.module` | the app and its modules | `io.github.org4j.java`, the Spring Modulith BOM, integration tests |
| `io.github.org4j.integration-test` | anything with integration tests | `src/integrationTest` (`*IT`) as a JVM test suite, run by `check` |
| `io.github.org4j.settings` | a build on org4j (settings plugin, its own jar: `gradle-settings`) | org4j from `release` or `local`, its catalog as `libs` (README.md §Building on org4j) |

Versions come from `gradle/libs.versions.toml`, the one catalog. A build that applies these plugins
needs it as `libs` (published as `io.github.org4j:version-catalog`): `io.github.org4j.java` checks up
front for every alias the plugins read and names any that is missing, and `io.github.org4j.library` takes the
library's version from its `gradle.properties`. The plugins carry the org4j version (`org4j`).
The contract plugin moved with
the contracts to [khakimjanovich/contracts](https://github.com/khakimjanovich/contracts).

## No workarounds

A problem in a repository we own (org4j, contracts, the services) is fixed there and released; one
outside (a library's API, a scheme's quirk) is isolated in one place with a comment naming it. `check`
fails on what marks a workaround instead (`verifyNoWorkarounds`, in `io.github.org4j.java`):

- in `src/main` and the build file: a comment saying `TODO`, `FIXME`, `HACK`, `XXX`, "workaround",
  "for now" or "temporary fix" (and the like: "temporary token" is a domain word, not a marker);
  `ignoreFailures` or `-x test`;
- in `src/main`: a `@SuppressWarnings` without a `//` reason on the line above;
- in any source set: a `@Disabled` test.

A `ponytail:` comment marks a deliberate simplification with its limit and upgrade path, listed in the
debt ledger; it is allowed. An open question goes to the backlog, not into the code.

## One architecture

`check` runs `verifyArchitecture` (in `io.github.org4j.java`) on the classes compiled from `src/main/java`;
generated code is left out. The rules are in `ArchitectureRules`, the same for every service:

- a controller calls services: never a repository, a service implementation, an entity, a mapper,
  a workflow or configuration;
- only a service implementation, a workflow or configuration reaches a repository;
- nothing calls a controller, and a controller is never its contract's client;
- a constant (a static final field of a value type: a primitive, a string, a number, a `Duration`, a
  `Pattern`, a `DateTimeFormatter`, a `Set`, `List` or `Map`) lives in a `ConstantUtils` or
  `*Constants` class, and such a class holds only static final fields and has a private constructor.
  An enum's values, `serialVersionUID` and objects that are not values (a logger, a mapper) are not constants.
- a utility class (`*Utils` or `*Util`) is `final`, declares only private constructors, and contains
  only static methods and static fields.
- no field injection (`@Autowired`, `@Value`, `@Inject`, `@Resource`): dependencies must be injected
  via explicit constructor injection.
- structured logging: production code never calls `System.out`, `System.err`, `printStackTrace()`,
  or `java.util.logging`; all application logging flows through SLF4J.
- test isolation: test frameworks (`org.junit`, `org.mockito`, `org.assertj`) never leak into `src/main`.
- non-blocking: production code never calls `Thread.sleep`.
- cycle-free package slices: packages under `io.github.org4j` are free of cyclic dependencies.

- no `Map<String, Object>` (or `Map<String, ?>`) as a field, a parameter or a return type: a
  request body or any other structure is a DTO. A tree whose fields must all be kept as they came
  (Jackson's `ObjectNode`) is not a map.
- no `MultiValueMap` (or `LinkedMultiValueMap`): a form body is typed `@RequestParam`s on an
  `@HttpExchange` method, a multipart body typed `@RequestPart`s.

A service (not a library, which is one flat package and its public API) also keeps the packages of
the Humo and Uzcard adapters:

| What | Package |
|---|---|
| `@RestController` / `@Controller` (`*Controller`) | `controller` |
| `@Service` | `service` |
| `@Repository` / `*Repository` | `repository` |
| `@Configuration` | `config` |
| `@ConfigurationProperties` | `property` |
| a `*Request`, `*Response` or `*Params` DTO | `model`, data only: no controller, service, client, config, property, repository or mapper |
| `*ExceptionStatus` | `constant` |

And it maintains strict layer boundaries:

- service layers (`controller`, `service`, `repository`, `config`, `property`, `model`, `client`, `mapper`, `workflow`) are free of cyclic dependencies;
- controllers never declare `@Transactional`: transaction boundaries live in the service layer;
- repositories never depend on services, controllers, or webmvc classes (`org.springframework.web..`);
- services never depend on the presentation layer (`controller`);

And it maps in one place:

- a controller passes the request to a service and returns the service's result: no client, no
  mapper, no JSON;
- a service returns a dedicated result and never touches JSON: a mapper in `mapper` turns an
  answer into that result;
- a mapper only maps: no service, no controller.

And it calls out in one place: an outgoing call is an `@HttpExchange` interface in `client`, with its
paths, built in `config` (as the Humo and Uzcard adapters do); a service never builds a request with
`RestClient`, `RestTemplate` or `WebClient`.

And it never builds a body from name, value pairs: no method takes a `String...` or `Object...`
(`main` aside), and nothing outside `mapper` builds a JSON tree (`ObjectNode`, `ArrayNode`,
`JsonNodeFactory`). A body is a DTO, or typed parameters of an `@HttpExchange` method.

And it throws one kind of error: outside `config` and `property` (which fail at startup, before any
request) the only throwable a class creates is `GeneralException` or a subclass of it, a problem
detail with a stable code. A case that cannot happen (no SHA-256, a cipher that fails) is
`GeneralException(ErrorExceptionStatus.INTERNAL_ERROR)`. A private constructor's guard (Lombok's
`@UtilityClass`) and what the compiler puts in an exhaustive `switch` (`MatchException`) pass. A library (`io.github.org4j.library`) keeps its own
exceptions; the service maps them.

A project without a layer (a library with no controller) passes the rules about it.

## org4j first

`check` runs `verifyOrg4jFirst` (in `io.github.org4j.java`, off in `io.github.org4j.library`): a service that
declares a library org4j already wraps fails, with org4j library to use instead.

| Declared directly | Use |
|---|---|
| `spring-boot-starter-data-redis`, `spring-data-redis`, Lettuce, Jedis, Redisson | `libs.valkey` (`ValkeyKeys`) |
| Spring Vault, `spring-cloud-starter-vault-config` | `libs.vault` (`VaultSecrets`, `VaultTransit`) |
| `keycloak-admin-client` | `libs.keycloak.admin` (`KeycloakAdmin`) |
| `HikariCP`, `liquibase-core` | `libs.persistence` (`@EnableModulePersistence`) |

Only what the build declares counts: what an org4j library brings with it is fine. In the code,
`verifyArchitecture` refuses a module's own `HikariDataSource`, `SpringLiquibase` or
`LocalContainerEntityManagerFactoryBean`: `@EnableModulePersistence` builds them.

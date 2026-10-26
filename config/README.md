# config

Loads every library's own settings and error definitions, so no app imports them. A library lists
its YAML files in `src/main/resources/META-INF/spring/uz.khakimjanovich.platform.config.LibraryDefaults.imports`,
one per line (`#` comments allowed, the format of Boot's `AutoConfiguration.imports`), and depends
on this library at runtime; it ships no Java for it.

```groovy
runtimeOnly project(':config')
```

```
# META-INF/spring/uz.khakimjanovich.platform.config.LibraryDefaults.imports
properties/otp-exception.yaml
```

`LibraryDefaults` runs right after Boot's config data, and adds each file last: the app's files,
profiles, `spring.config.import`, environment variables and arguments all override it. A file
already loaded (a parent and child context) is added once. No properties, errors or beans of its own.

## Required settings

No setting has a default. A `${VAR}` in any configuration file (the app's or a library's) that
nothing resolves stops startup once the context is prepared (`RequiredSettings`), naming every one
missing; Spring's binder would otherwise bind the literal text. Values a test registers
(`@DynamicPropertySource`) count. An empty value is a value.

## Tests

Unit tests only (`./gradlew -p platform :config:test`): a test `.imports` file, overridden by a
config file and by a command-line argument, and added once to a parent and child. Each library's
integration test boots against its loaded values.

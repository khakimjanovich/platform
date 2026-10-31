# masking

Used by observability (every log line, every client call) and error (its log line). The library
knows only generic names; a service adds its own fields with `platform.observability.masking.*`,
never by copying the lists, and no app's field name is written here.

```groovy
implementation libs.platform.masking
```

Hides what must never reach a log: card numbers (first 6 and last 4 digits kept), tokens (last 4
characters kept) and secrets (hidden whole), by field name, in JSON, XML, forms, URI queries and
`key=value` text (XML `<name>`/`<value>` pairs too), plus any run of 13 to 19 digits. The `value`
of any object with a `type` is a secret (a typed credential, as Keycloak's are). A service may add
fields that are secret only in context: `code: [pan, otp]` hides a JSON `code` beside a `pan` or
under `otp`, and leaves an error's `code` readable. Plain Java and Jackson, no Spring.

It errs on the side of hiding: every run of 13 to 19 digits is masked, with no Luhn check (epoch
millis and long ids too), and text hides any `code` value, so log an error code as `error CODE`,
not `code=CODE`.

```java
var masking = new Masking(MaskingRules.defaults()
    .with(List.of("EMBOS_NAME"), List.of("hpan"), List.of(), Map.of("code", List.of("pan", "otp"))));
masking.body(bytes, "application/json");
masking.uri(uri);
masking.text(message);
```

`MaskingRules.with(...)` adds names to the defaults; nothing repeats them. No properties of its
own (it has no Spring), no errors, no beans.

## Tests

Unit tests only (`./gradlew -p platform :masking:test`): the library is pure functions with no
infrastructure, so its unit tests are its integration surface. Its wiring into logs is tested by
observability's integration test.

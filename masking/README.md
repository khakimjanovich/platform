# masking

Hides what must never reach a log: card numbers (first 6 and last 4 digits kept), tokens (last 4
characters kept) and secrets (hidden whole), by field name, in JSON, XML, forms, URI queries and
`key=value` text, plus any run of 13 to 19 digits. Plain Java and Jackson, no Spring.

```java
var masking = new Masking(MaskingRules.defaults().with(List.of("EMBOS_NAME"), List.of("hpan"), List.of()));
masking.body(bytes, "application/json");
masking.uri(uri);
masking.text(message);
```

`MaskingRules.with(...)` adds names to the defaults; nothing repeats them.

## Tests

Unit tests only (`./gradlew -p platform :masking:test`): the library is pure functions with no
infrastructure, so its unit tests are its integration surface. Its wiring into logs is tested by
observability's integration test.

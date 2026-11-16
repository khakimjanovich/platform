# masking

Used by observability (every log line, every client call) and error (its log line). The library
knows only generic names; a service configures its logging masking via `platform.logging.masking.*` in YAML or Vault,
and no app's field name is hardcoded here.

```groovy
implementation libs.platform.masking
```

Masks sensitive fields across JSON, XML, forms, URI query parameters, and plain text patterns (`key=value`, `key: value`, `key value`, `/key/value`).

### Zero-Default Policy & Mask Format

- **Zero Defaults**: By default, `Masking.defaults()` returns an empty rule set (`Masking.empty()`). In the absence of explicit field configuration via Spring Boot YAML or HashiCorp Vault, content streams unmasked without arbitrary guesses or regex assumptions.
- **Dot-Masking Format**:
  - Fields $\ge 10$ characters (e.g., PANs, tokens): First 6 and last 4 characters are preserved, with intermediate characters replaced with dots (`860030......7890`).
  - Fields $< 10$ characters (e.g., CVVs, short PINs): 1:1 replacement with dots (`...`).
  - Custom mask character can be configured (e.g., `*`).

```java
var masking = new Masking(MaskingRules.of(List.of("pan", "cvv", "access_token")));
masking.body(bytes, "application/json");
masking.uri(uri);
masking.text(message);
```

## Tests

Unit tests only (`./gradlew -p platform :masking:test`): the library consists of pure functions with no
infrastructure dependencies. Wiring into logs is verified by observability's integration test.

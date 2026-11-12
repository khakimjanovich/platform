# vault

Secrets from HashiCorp Vault (KV version 2) through Spring Cloud Vault: read only and failing
closed, with Vault's own sign-in (a token locally, AppRole or Kubernetes elsewhere) and token
renewal. It also makes Vault the app's source of every secret (`spring.config.import`), keeps
API clients' request-signing keys a short time so a burst of signed requests reads Vault once, and
seals values with Vault Transit, whose keys never leave Vault.

```groovy
implementation libs.platform.vault
```

```yaml
spring:
  config:
    import: ${VAULT_IMPORT} # vault://secret/<service>: a Vault that can't be read stops startup
  cloud:
    vault:
      uri: ${VAULT_URL}
      authentication: ${VAULT_AUTHENTICATION} # TOKEN, APPROLE, KUBERNETES
      token: ${VAULT_TOKEN} # locally local-only; none has a default
      fail-fast: ${VAULT_FAIL_FAST}
```

`VaultSecrets.get(path)` gives the latest version of a secret, empty if there is none, and throws
`VaultUnavailableException` if Vault can't be read: it refuses, or it doesn't answer at all.
`SigningKeys` (with `platform.vault.signing-keys.path`): `find(clientId)` gives a client's signing
key (base64 in Vault, at the app's path and field), empty if none is set or the id can't be a
client id (`[A-Za-z0-9_-][A-Za-z0-9._-]*`, never a path). Where the keys are is the app's own: the
library names no path or field.
`ClientRegistry` (with `platform.vault.clients.path`) is an app's clients kept in one KV entry, one
key per client id, its value the client's record: `find(clientId)` and `all()` give them as they are,
unread (the record's format is the app's, parsed by the app alone). The entry is read once per
`cache-ttl`; past that a failed read throws, never an old copy, so a client added or taken away in
Vault counts without a restart. It holds an app's own per-client data, never permissions: who may
call what is granted in Keycloak.

`VaultTransit` (with `platform.vault.transit.mount`): the module names the key and gives a context
that says where the value lives (`<table>.<column>:<row id>`); with a derived key
(`derived=true`) a ciphertext copied to another row or column does not open.

```java
var sealed = transit.encrypt(KEY, "item.secret:" + id, plain);
var plain = transit.decrypt(KEY, "item.secret:" + id, sealed);
var index = transit.hmac(INDEX_KEY, 1, value); // blind index: the same value, the same hash
```

After Vault rotates a key, the module's own job moves every value that does not start with
`latestPrefix(KEY)` (`vault:v<n>:`) with `rewrap(KEY, context, sealed)`: the plaintext never leaves
Vault. Once no value is left on an old version, the operator may retire it
(`min_decryption_version`). The HMAC key's version is pinned by the module: a rotation would change
every index. The library names no key: the infra creates them (`vault secrets enable transit`,
`vault write -f transit/keys/<key> derived=true`), and the module's settings name them.

## Properties

| Property | Default | What |
|---|---|---|
| `platform.vault.mount` | `secret` | the KV version 2 mount |
| `platform.vault.signing-keys.path` | none | a client's key entry, `%s` its client id (required in the path); unset, there are no signing keys |
| `platform.vault.signing-keys.field` | none, required with `path` | the entry's key holding the base64 signing key |
| `platform.vault.signing-keys.cache-ttl` | none, required with `path` | how long a key read is used; a missing key is never kept, and a Vault failure never answers with an old key |
| `platform.vault.clients.path` | none | the client registry's KV entry; unset, there is no registry |
| `platform.vault.clients.cache-ttl` | none, required with `path` | how long one read of the registry is used |
| `platform.vault.transit.mount` | none | the Transit engine's mount; unset, there is no `VaultTransit` |

Where Vault is and how to sign in are Spring Cloud Vault's `spring.cloud.vault.*`.

## Errors

`VaultUnavailableException`: callers fail closed (the payment module refuses the request). From
`VaultTransit` it also means a refused key or a ciphertext that does not open with its context; the
log names Vault's status and path, never a value.

## Health

With the actuator, `vaultToken`: UP while Vault accepts the app's token (a lookup of itself). Spring
Cloud Vault's own `vault` checks that Vault is up and unsealed.

## Beans you can replace

`VaultSecrets`, `SigningKeys`, `VaultTransit`.

## Tests

- `./gradlew -p platform :vault:test`: the signing-key cache (one read per TTL, misses, failures),
  ids that aren't client ids, the path check, and that a path without its field or cache time
  stops startup.
- `./gradlew -p platform :vault:integrationTest`: a real Vault: token and AppRole sign-in, latest
  version, missing path, wrong token, rotation after the TTL, and Vault as a configuration source;
  Transit: a value opens only with its own context, rewrap after a rotation, a stable HMAC, a
  refused token, and no `VaultTransit` without a mount.

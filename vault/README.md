# vault

Secrets from HashiCorp Vault (KV version 2) through Spring Cloud Vault: read only and failing
closed, with Vault's own sign-in (a token locally, AppRole or Kubernetes elsewhere) and token
renewal. It also makes Vault the app's source of every secret (`spring.config.import`), and keeps
merchants' request-signing keys a short time so a burst of signed requests reads Vault once.

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
`SigningKeys.find(clientId)` gives a merchant's signing key (base64 in Vault), empty if the bank
hasn't set one or the id can't be a client id (`[A-Za-z0-9_-][A-Za-z0-9._-]*`, never a path).

## Properties

| Property | Default | What |
|---|---|---|
| `platform.vault.mount` | `secret` | the KV version 2 mount |
| `platform.vault.signing-keys.path` | `merchants/%s` | a client's key path, `%s` its client id (required in the path) |
| `platform.vault.signing-keys.cache-ttl` | 30s | how long a key read is used; a missing key is never kept, and a Vault failure never answers with an old key |

Where Vault is and how to sign in are Spring Cloud Vault's `spring.cloud.vault.*`.

## Errors

`VaultUnavailableException`: callers fail closed (the payment module refuses the request).

## Health

With the actuator, `vaultToken`: UP while Vault accepts the app's token (a lookup of itself). Spring
Cloud Vault's own `vault` checks that Vault is up and unsealed.

## Beans you can replace

`VaultSecrets`, `SigningKeys`.

## Tests

- `./gradlew -p platform :vault:test`: the signing-key cache (one read per TTL, misses, failures),
  ids that aren't client ids, the path check.
- `./gradlew -p platform :vault:integrationTest`: a real Vault: token and AppRole sign-in, latest
  version, missing path, wrong token, rotation after the TTL, and Vault as a configuration source.

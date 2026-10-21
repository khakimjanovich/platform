# keycloak

Keycloak's admin API for the few things modules need: users (with a password, a verified email
and realm roles) and machine-to-machine clients with rotating secrets. It logs in as its own
confidential client with client credentials, refreshing the token through Spring Security.

```groovy
implementation libs.ecom.keycloak
```

```java
var id = keycloak.createUser(email, email, password, false);
keycloak.markEmailVerified(id);
keycloak.grantRealmRole(id, "partner");
var client = keycloak.createConfidentialClient("shop-1", "merchant"); // secret shown only here
var secret = keycloak.rotateSecret(client.id());
```

## Properties

| Property | Default | What |
|---|---|---|
| `ecom.keycloak.url` | — | Keycloak's base URL; setting it turns the library on |
| `ecom.keycloak.realm` | — | the realm managed |
| `ecom.keycloak.client-id` | — | the backend's confidential client, allowed to use the admin API |
| `ecom.keycloak.client-secret` | — | that client's secret, from Vault |
| `ecom.keycloak.connect-timeout` | 5s | for every call, the token request included |
| `ecom.keycloak.read-timeout` | 10s | for every call, the token request included |

## Errors

| Code | Status | When |
|---|---|---|
| `KEYCLOAK_CONFLICT` | 409 | what was created already exists; the parameter names it |
| `KEYCLOAK_UNAVAILABLE` | 503 | any other error status, a timeout, no token, or a broken answer; Keycloak's own message is a sub-error |

## Beans you can replace

`KeycloakAdmin`.

## Tests

- `./gradlew -p platform :keycloak:test`: every failure as its error, the read timeout, the
  property checks.
- `./gradlew -p platform :keycloak:integrationTest`: a real Keycloak with an imported realm: users,
  roles, logins, secrets that rotate, and Keycloak stopped mid-life.

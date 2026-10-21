# security

One way to secure every service: Keycloak access tokens (realm roles as `ROLE_*`), route rules
from configuration where anything not listed is refused, public routes that ignore any token sent,
401 and 403 as problem details, and client-credentials tokens on calls to other services.

```groovy
implementation libs.ecom.security
```

Set Boot's `spring.security.oauth2.resourceserver.jwt.issuer-uri` (the realm) and
`spring.security.oauth2.resourceserver.jwt.audiences` (the API's audience, `ecom-api`).

## Properties

| Property | What |
|---|---|
| `ecom.security.public-routes` | routes anyone may call; a token sent with them is never read. `"POST /api/v1/registrations/**"` limits one to a method |
| `ecom.security.roles` | each route and the realm role it needs, in order, e.g. `"[/api/v1/admin/**]": ADMIN` |
| `ecom.security.service-tokens.groups` | HTTP service groups and the OAuth2 client registration (`spring.security.oauth2.client.registration.<id>`) their calls take a client-credentials token from |

## Adding to the chain

A module adds a filter with a `Customizer<HttpSecurity>` bean, which Spring Security applies before
the library's rules: `http -> http.addFilterAfter(filter, BearerTokenAuthenticationFilter.class)`.
To refuse a request, hand `ProblemSecurityHandler` the reason so the answer is a problem detail.
Filters only; the route rules stay in configuration.

## Calling another service

With an OAuth2 client registration and `ecom.security.service-tokens.groups.<group>=<registration>`,
the group's `@HttpExchange` clients send the service's own client-credentials token (Spring
Security's interceptor gets and refreshes it). A 401 drops the token and the call is tried once
more. Without registrations nothing changes. The registration's secret comes from Vault.

`CurrentUser.name()` and `CurrentUser.clientId()` say who is calling, or null without a token.

## Errors

`UNAUTHORIZED` (401) and `FORBIDDEN` (403), defined by the error library.

## Beans you can replace

| Bean | Replace with |
|---|---|
| `apiSecurity` (`SecurityFilterChain`) | your own chain, when a service needs a different one |
| `KeycloakJwtConverter` | your own token-to-authorities converter |
| `bearerTokenResolver` | your own `BearerTokenResolver` |
| `ServiceTokens` | your own `RestClientHttpServiceGroupConfigurer` |

## Tests

- `./gradlew -p platform :security:test`: route rules, public routes with stale tokens, module
  customizers, the token converter, service tokens armed and dormant.
- `./gradlew -p platform :security:integrationTest`: a real Keycloak with password and
  client-credentials tokens: roles, the audience, stale tokens on public routes, a module's
  refusing filter, service tokens with a role check, one retry on 401 and a rotated secret.

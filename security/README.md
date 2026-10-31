# security

One way to secure every service: Keycloak access tokens (realm roles as `ROLE_*`), route rules
from configuration where anything not listed is refused, public routes that ignore any token sent,
401 and 403 as problem details, and client-credentials tokens on calls to other services.

```groovy
implementation libs.platform.security
```

Set Boot's `spring.security.oauth2.resourceserver.jwt.issuer-uri` (the realm) and
`spring.security.oauth2.resourceserver.jwt.audiences` (the API's audience, e.g. `demo-api`).

A service that takes tokens from several issuers (another unit's Keycloak, each with its own clients)
lists them in `platform.security.issuers` instead: each issuer's tokens must name its `audience` and
come from one of its `clients` (`azp`), and only the realm `roles` listed for it count (one unit's
Keycloak can't grant another's roles); a token from any other issuer or client is refused (401), and
each issuer's keys are fetched with its first token. An issuer that can't be reached then answers
500, as Boot's one issuer does, and is tried again on the next request. One issuer may be listed again for other
clients, each entry with the roles its clients may hold (one unit's Keycloak, a card-number role for
one caller only). `issuers` and Boot's `issuer-uri` together stop startup, as do an incomplete
issuer, a client of one issuer listed twice and one issuer with two audiences.

## Properties

| Property | What |
|---|---|
| `platform.security.public-routes` | routes anyone may call; a token sent with them is never read. `"POST /api/v1/registrations/**"` limits one to a method |
| `platform.security.roles` | each route and the realm role it needs, in order, e.g. `"[/api/v1/admin/**]": ADMIN` |
| `platform.security.issuers[n].uri`, `.audience`, `.clients`, `.roles` | trusted issuers, instead of Boot's one `issuer-uri`: each issuer's URI (its tokens' `iss`), the audience its tokens must name, the clients whose tokens are accepted, and the realm roles they may hold (others in their tokens are dropped); one issuer may have several entries, one per set of clients |
| `platform.security.issuers[n].client-group` | instead of `.clients`: a group of clients known only while the app runs, asked of the app's `ClientGroups` bean on each token (the app declares it from its own client records, e.g. read through platform/vault's `ClientRegistry`; neither library knows the other); a client the bean doesn't place in the group, or can't, is refused. An entry has `clients` or `client-group`, not both |
| `platform.security.ssl-bundle` | an SSL bundle (`spring.ssl.bundle.*`) whose trust store the trusted issuers' keys are fetched with, for a Keycloak whose certificate comes from the app's own CA; unset, the JVM's trust store. Needs `issuers`; a bundle not defined stops startup |
| `platform.security.service-tokens.groups` | HTTP service groups and the OAuth2 client registration (`spring.security.oauth2.client.registration.<id>`) their calls take a client-credentials token from |
| `platform.security.service-tokens.connect-timeout` | 5s: a token request's wait for a connection |
| `platform.security.service-tokens.read-timeout` | 10s: a token request's wait for its answer |
| `platform.security.service-tokens.ssl-bundle` | an SSL bundle whose trust the token requests go over, as `ssl-bundle` for the keys; unset, the JVM's trust store |

## Adding to the chain

A module adds a filter with a `Customizer<HttpSecurity>` bean, which Spring Security applies before
the library's rules: `http -> http.addFilterAfter(filter, BearerTokenAuthenticationFilter.class)`.
To refuse a request, hand `ProblemSecurityHandler` the reason so the answer is a problem detail.
Filters only; the route rules stay in configuration: a customizer that sets route rules
(`authorizeHttpRequests`) stops startup.

## Calling another service

With an OAuth2 client registration and `platform.security.service-tokens.groups.<group>=<registration>`,
the group's `@HttpExchange` clients send the service's own client-credentials token (Spring
Security's interceptor gets and refreshes it). A 401 that refuses the token (no challenge, or
`error="invalid_token"`) drops it and the call is tried once more; `insufficient_scope` is the
answer. The app's own `OAuth2AuthorizedClientManager` bean gets the tokens when it has one;
otherwise a manager over Boot's authorized-client store does (Spring Security's default needs a
servlet request, which a job's call has not), its token requests over Boot's `RestClient.Builder`.
Without registrations nothing changes. The registration's secret comes from Vault.

`CurrentUser.name()` and `CurrentUser.clientId()` say who is calling, or null without a token.

## Errors

401 and 403 are answered by the app's `AuthenticationEntryPoint` and `AccessDeniedHandler` beans;
the library has none of its own and doesn't depend on error. Without them the app doesn't start. With
the error library on the classpath, its `ProblemSecurityHandler` is both, so the answers are
`UNAUTHORIZED` (401) and `FORBIDDEN` (403) problem details.

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
  refusing filter, service tokens with a role check, one retry on 401 and a rotated secret;
  trusted issuers over three realms: a listed client of either trusted issuer, a client not listed,
  an issuer not trusted, a listed client without the route's role; a Keycloak over HTTPS only, its
  certificate from a test CA (`tls/`) the JVM doesn't trust: the token and the keys over the bundles.

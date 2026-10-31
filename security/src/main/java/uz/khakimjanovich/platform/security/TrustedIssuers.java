package uz.khakimjanovich.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.web.client.RestTemplate;

/**
 * Tokens from several issuers, each with the clients it may send and the roles each client may
 * hold: the token's issuer picks its keys, and a token from any other issuer, for another audience,
 * or from a client not listed is refused (401); a role its client may not hold doesn't count. One
 * issuer may be listed for several clients with different roles. Each issuer's keys are fetched
 * with its first token, not at startup, over the given SSL bundle's trust when there is one.
 */
final class TrustedIssuers {

  private TrustedIssuers() {}

  /** One entry of an issuer: which clients (fixed, or a group asked at runtime) and their roles. */
  private record Entry(Predicate<String> clients, Set<String> roles) {}

  static AuthenticationManagerResolver<HttpServletRequest> resolver(
      List<SecurityProperties.Issuer> issuers,
      KeycloakJwtConverter converter,
      @Nullable SslBundle bundle,
      @Nullable ClientGroups groups) {
    // Per issuer: its one audience (SecurityProperties checks it) and its entries.
    Map<String, String> audiences = new HashMap<>();
    Map<String, List<Entry>> entries = new HashMap<>();
    for (var issuer : issuers) {
      audiences.put(issuer.uri(), issuer.audience());
      var roles =
          issuer.roles().stream().map(KeycloakJwtConverter::authority).collect(Collectors.toSet());
      var group = issuer.clientGroup();
      Predicate<String> clients;
      if (group == null) {
        clients = Set.copyOf(issuer.fixedClients())::contains;
      } else {
        if (groups == null) {
          throw new IllegalStateException(
              SecurityConstants.CLIENT_GROUPS_MISSING.formatted(issuer.uri(), group));
        }
        clients = client -> groups.contains(group, client);
      }
      entries
          .computeIfAbsent(issuer.uri(), uri -> new ArrayList<>())
          .add(new Entry(clients, roles));
    }
    Map<String, AuthenticationManager> managers = new HashMap<>();
    entries.forEach(
        (uri, list) -> {
          // The roles a client may hold: those of every entry it belongs to now; none, refused.
          Function<@Nullable String, Set<String>> allowed =
              client ->
                  client == null
                      ? Set.of()
                      : list.stream()
                          .filter(entry -> entry.clients().test(client))
                          .flatMap(entry -> entry.roles().stream())
                          .collect(Collectors.toSet());
          var provider =
              new JwtAuthenticationProvider(
                  decoder(
                      uri,
                      Objects.requireNonNull(audiences.get(uri)),
                      client -> !allowed.apply(client).isEmpty(),
                      bundle));
          provider.setJwtAuthenticationConverter(
              jwt -> {
                var token = converter.convert(jwt);
                // Only the roles its client may hold count; any other in the token is dropped. The
                // decoder already refused a client not listed.
                var held = allowed.apply(jwt.getClaimAsString(SecurityConstants.CLIENT_CLAIM));
                var roles =
                    token.getAuthorities().stream()
                        .filter(authority -> held.contains(authority.getAuthority()))
                        .toList();
                return new JwtAuthenticationToken(jwt, roles, token.getName());
              });
          managers.put(uri, new ProviderManager(provider));
        });
    return new JwtIssuerAuthenticationManagerResolver(
        uri -> {
          var manager = managers.get(uri);
          if (manager == null) {
            throw new InvalidBearerTokenException(SecurityConstants.ISSUER_NOT_TRUSTED);
          }
          return manager;
        });
  }

  static JwtDecoder decoder(
      String uri, String audience, Predicate<String> clients, @Nullable SslBundle bundle) {
    return new SupplierJwtDecoder(
        () -> {
          var location = NimbusJwtDecoder.withIssuerLocation(uri);
          // Its discovery document and keys, over the bundle's trust (JDK client: host names are
          // checked).
          if (bundle != null) {
            location.restOperations(
                new RestTemplate(
                    new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().sslContext(bundle.createSslContext()).build())));
          }
          var decoder = location.build();
          decoder.setJwtValidator(
              new DelegatingOAuth2TokenValidator<>(
                  JwtValidators.createDefaultWithIssuer(uri),
                  new JwtClaimValidator<List<String>>(
                      JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)),
                  new JwtClaimValidator<String>(
                      SecurityConstants.CLIENT_CLAIM,
                      client -> client != null && clients.test(client))));
          return decoder;
        });
  }
}

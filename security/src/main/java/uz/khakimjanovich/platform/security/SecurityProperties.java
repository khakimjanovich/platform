package uz.khakimjanovich.platform.security;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Who may call what. {@code public} routes need no token; {@code roles} maps a route to the realm
 * role it needs, checked in the order written. Any route not listed is refused. A route may start
 * with an HTTP method: {@code "POST /api/v1/registrations/**"}. {@code issuers} trusts tokens from
 * several issuers, each for its own clients, instead of Boot's one {@code issuer-uri}.
 *
 * @param publicRoutes routes anyone may call, without a token
 * @param roles each route and the realm roles that may call it (one, or several separated by
 *     commas: any of them), in order; a route not listed is refused
 * @param issuers trusted issuers, each with the audience its tokens must name and the clients whose
 *     tokens are accepted; empty, Boot's one issuer-uri is used
 */
@ConfigurationProperties(SecurityConstants.PROPERTIES_PREFIX)
public record SecurityProperties(
    @DefaultValue List<String> publicRoutes,
    @DefaultValue LinkedHashMap<String, List<String>> roles,
    @DefaultValue List<Issuer> issuers) {

  /**
   * A route with no role, or a blank one, would quietly let nobody in: refused at startup.
   *
   * @param publicRoutes routes anyone may call, without a token
   * @param roles each route and the realm roles that may call it (one, or several separated by
   *     commas: any of them), in order; a route not listed is refused
   * @param issuers trusted issuers, each with the audience its tokens must name and the clients
   *     whose tokens are accepted; empty, Boot's one issuer-uri is used
   */
  public SecurityProperties {
    issuers.forEach(Issuer::check);
    // One issuer may be listed for several clients, each with the roles it may hold; the same
    // client twice, or one issuer with two audiences, would be unclear.
    var clients = new HashSet<String>();
    var audiences = new HashMap<String, String>();
    for (var issuer : issuers) {
      for (var client : issuer.clients()) {
        if (!clients.add(issuer.uri() + " " + client)) {
          throw new IllegalArgumentException(
              SecurityConstants.ISSUER_CLIENT_TWICE.formatted(client, issuer.uri()));
        }
      }
      if (!audiences
          .computeIfAbsent(issuer.uri(), uri -> issuer.audience())
          .equals(issuer.audience())) {
        throw new IllegalArgumentException(
            SecurityConstants.ISSUER_AUDIENCES.formatted(issuer.uri()));
      }
    }
    roles.forEach(
        (route, names) -> {
          if (names.isEmpty() || names.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(SecurityConstants.ROLE_MISSING.formatted(route));
          }
        });
  }

  /**
   * An issuer whose tokens are accepted: its tokens must name {@code audience} and come from one of
   * {@code clients} (the {@code azp} claim), and only the realm {@code roles} listed here count;
   * any other role its tokens carry is dropped, so one unit's Keycloak can't grant another's roles.
   *
   * @param uri the issuer, as its tokens' {@code iss} says, e.g. a Keycloak realm's URL
   * @param audience the audience its tokens must name: this API
   * @param clients the clients whose tokens are accepted
   * @param roles the realm roles this issuer may grant
   */
  public record Issuer(String uri, String audience, List<String> clients, List<String> roles) {

    // Constructor binding passes null for a value not set at all.
    void check() {
      if (uri == null
          || uri.isBlank()
          || audience == null
          || audience.isBlank()
          || clients == null
          || clients.isEmpty()
          || roles == null
          || roles.isEmpty()) {
        throw new IllegalArgumentException(SecurityConstants.ISSUER_INCOMPLETE.formatted(uri));
      }
    }
  }
}

package uz.khakimjanovich.platform.security;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Who may call what. {@code public} routes need no token; {@code signed-in} routes need a valid
 * token and no particular role; {@code roles} maps a route to the realm role it needs, checked in
 * the order written. Any route not listed is refused. A route may start with an HTTP method: {@code
 * "POST /api/v1/registrations/**"}. {@code issuers} trusts tokens from several issuers, each with
 * the roles it may grant, instead of Boot's one {@code issuer-uri}.
 *
 * @param publicRoutes routes anyone may call, without a token
 * @param signedInRoutes routes any caller with a valid token may call, whatever its roles; the
 *     operation decides what it gives
 * @param roles each route and the realm roles that may call it (one, or several separated by
 *     commas: any of them), in order; a route not listed is refused
 * @param issuers trusted issuers, each with the audience its tokens must name and the roles it may
 *     grant; empty, Boot's one issuer-uri is used
 * @param sslBundle the SSL bundle ({@code spring.ssl.bundle.*}) whose trust store the issuers' keys
 *     are fetched with, for an issuer whose certificate comes from the app's own CA; unset, the
 *     JVM's trust store
 */
@ConfigurationProperties(SecurityConstants.PROPERTIES_PREFIX)
public record SecurityProperties(
    @DefaultValue List<String> publicRoutes,
    @DefaultValue List<String> signedInRoutes,
    @DefaultValue LinkedHashMap<String, List<String>> roles,
    @DefaultValue List<Issuer> issuers,
    @Nullable String sslBundle) {

  /**
   * A route with no role, or a blank one, would quietly let nobody in: refused at startup.
   *
   * @param publicRoutes routes anyone may call, without a token
   * @param signedInRoutes routes any caller with a valid token may call, whatever its roles
   * @param roles each route and the realm roles that may call it (one, or several separated by
   *     commas: any of them), in order; a route not listed is refused
   * @param issuers trusted issuers, each with the audience its tokens must name and the roles it
   *     may grant; empty, Boot's one issuer-uri is used
   * @param sslBundle the SSL bundle the issuers' keys are fetched with; unset, the JVM's trust
   *     store
   */
  public SecurityProperties {
    issuers.forEach(Issuer::check);
    // Boot's one issuer-uri fetches its keys itself: a bundle there would quietly be ignored.
    if (sslBundle != null && issuers.isEmpty()) {
      throw new IllegalArgumentException(SecurityConstants.SSL_BUNDLE_WITHOUT_ISSUERS);
    }
    // One entry per issuer: two would make its audience and roles unclear.
    var uris = new HashSet<String>();
    for (var issuer : issuers) {
      if (!uris.add(issuer.uri())) {
        throw new IllegalArgumentException(SecurityConstants.ISSUER_TWICE.formatted(issuer.uri()));
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
   * An issuer whose tokens are accepted: its tokens must name {@code audience}, and only the realm
   * {@code roles} listed here count; any other role its tokens carry is dropped, so one unit's
   * Keycloak can't grant another's roles. Which caller holds which role is granted in that Keycloak
   * alone.
   *
   * @param uri the issuer, as its tokens' {@code iss} says, e.g. a Keycloak realm's URL
   * @param audience the audience its tokens must name: this API
   * @param roles the realm roles this issuer may grant
   */
  public record Issuer(String uri, String audience, List<String> roles) {

    // Constructor binding passes null for a value not set at all.
    void check() {
      if (uri == null
          || uri.isBlank()
          || audience == null
          || audience.isBlank()
          || roles == null
          || roles.isEmpty()) {
        throw new IllegalArgumentException(SecurityConstants.ISSUER_INCOMPLETE.formatted(uri));
      }
    }
  }
}

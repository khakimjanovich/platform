package uz.heeypay.ecom.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * A public route never reads a token, so a stale or broken {@code Authorization} header (a client
 * that always sends one) can't turn it into a 401. Everywhere else, the usual bearer token.
 */
public class PublicRouteTokenResolver implements BearerTokenResolver {

  private final RequestMatcher publicRoutes;
  private final BearerTokenResolver tokens = new DefaultBearerTokenResolver();

  public PublicRouteTokenResolver(RequestMatcher publicRoutes) {
    this.publicRoutes = publicRoutes;
  }

  @Override
  public String resolve(HttpServletRequest request) {
    return publicRoutes.matches(request) ? null : tokens.resolve(request);
  }
}

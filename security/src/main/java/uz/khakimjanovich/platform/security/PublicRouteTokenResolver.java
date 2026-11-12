package uz.khakimjanovich.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class PublicRouteTokenResolver implements BearerTokenResolver {

  private final RequestMatcher publicRoutes;
  private final BearerTokenResolver tokens = new DefaultBearerTokenResolver();

  public PublicRouteTokenResolver(RequestMatcher publicRoutes) {
    this.publicRoutes = publicRoutes;
  }

  @Override
  public @Nullable String resolve(HttpServletRequest request) {
    return publicRoutes.matches(request) ? null : tokens.resolve(request);
  }
}

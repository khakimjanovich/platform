package uz.khakimjanovich.platform.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import uz.khakimjanovich.platform.error.ProblemSecurityHandler;

/**
 * Adding the library secures the app: every call carries a Keycloak access token except the
 * configured public routes, which ignore any token sent; each listed route needs its role, anything
 * else is refused, and 401 and 403 answer with problem details. Boot's {@code
 * spring.security.oauth2.resourceserver.jwt.audiences} makes tokens name our API.
 *
 * <p>A module adds to this one chain with a {@code Customizer<HttpSecurity>} bean, which Spring
 * Security applies to the {@link HttpSecurity} before these rules, e.g. {@code http.addFilterAfter(
 * filter, BearerTokenAuthenticationFilter.class)}; to refuse a request it hands the {@link
 * ProblemSecurityHandler} the reason, so the refusal is a problem detail. Filters only: the route
 * rules stay here. An app that needs a different chain defines its own {@link SecurityFilterChain}.
 */
@AutoConfiguration(
    beforeName = {
      SecurityConstants.BOOT_WEB_SECURITY,
      SecurityConstants.BOOT_MANAGEMENT_SECURITY,
      SecurityConstants.BOOT_RESOURCE_SERVER_SECURITY,
      SecurityConstants.BOOT_OAUTH2_CLIENT_SECURITY
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityProperties.class)
// A route admits roles; an operation may narrow them further with @PreAuthorize.
@EnableMethodSecurity
public class SecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  KeycloakJwtConverter keycloakJwtConverter() {
    return new KeycloakJwtConverter();
  }

  @Bean
  @ConditionalOnMissingBean
  BearerTokenResolver bearerTokenResolver(SecurityProperties properties) {
    var routes = properties.publicRoutes().stream().map(SecurityAutoConfiguration::route).toList();
    RequestMatcher publicRoutes =
        routes.isEmpty() ? request -> false : new OrRequestMatcher(routes);
    return new PublicRouteTokenResolver(publicRoutes);
  }

  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      SecurityProperties properties,
      ProblemSecurityHandler problems,
      KeycloakJwtConverter converter,
      BearerTokenResolver tokens,
      Environment environment,
      ObjectProvider<SslBundles> bundles) {
    // Spring Security applies the Customizer<HttpSecurity> beans first, and the first matching
    // rule wins: a module's own route rule would silently beat the configured ones.
    if (hasRouteRules(http)) {
      throw new IllegalStateException(SecurityConstants.ROUTES_IN_CONFIGURATION);
    }
    if (!properties.issuers().isEmpty()
        && environment.containsProperty(SecurityConstants.BOOT_ISSUER_URI)) {
      throw new IllegalStateException(SecurityConstants.ISSUERS_AND_ISSUER_URI);
    }
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            routes -> {
              properties
                  .publicRoutes()
                  .forEach(route -> routes.requestMatchers(route(route)).permitAll());
              properties
                  .roles()
                  .forEach(
                      (route, role) ->
                          routes
                              .requestMatchers(route(route))
                              .hasAnyRole(role.toArray(String[]::new)));
              routes.anyRequest().denyAll();
            })
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .oauth2ResourceServer(
            server -> {
              server
                  .bearerTokenResolver(tokens)
                  .authenticationEntryPoint(problems)
                  .accessDeniedHandler(problems);
              // Several trusted issuers, or Boot's one issuer-uri.
              if (properties.issuers().isEmpty()) {
                server.jwt(jwt -> jwt.jwtAuthenticationConverter(converter));
              } else {
                // A bundle not defined stops startup here (NoSuchSslBundleException).
                var bundle =
                    properties.sslBundle() == null
                        ? null
                        : bundles.getObject().getBundle(properties.sslBundle());
                server.authenticationManagerResolver(
                    TrustedIssuers.resolver(properties.issuers(), converter, bundle));
              }
            })
        .build();
  }

  /** "POST /path/**" matches that method only; "/path/**" matches every method. */
  static RequestMatcher route(String route) {
    var parts = route.trim().split(SecurityConstants.METHOD_SEPARATOR, 2);
    var paths = PathPatternRequestMatcher.withDefaults();
    return parts.length == 2
        ? paths.matcher(HttpMethod.valueOf(parts[0]), parts[1].trim())
        : paths.matcher(parts[0]);
  }

  // Spring Security looks a configurer up by its raw class; AuthorizeHttpRequestsConfigurer is
  // generic, and no class literal can name its type argument.
  @SuppressWarnings("unchecked")
  private static boolean hasRouteRules(HttpSecurity http) {
    return http.getConfigurer(AuthorizeHttpRequestsConfigurer.class) != null;
  }
}

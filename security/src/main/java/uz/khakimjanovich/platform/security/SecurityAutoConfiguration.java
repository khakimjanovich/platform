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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

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
      AuthenticationEntryPoint unauthenticated,
      AccessDeniedHandler denied,
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
                  .signedInRoutes()
                  .forEach(route -> routes.requestMatchers(route(route)).authenticated());
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
            errors -> errors.authenticationEntryPoint(unauthenticated).accessDeniedHandler(denied))
        .oauth2ResourceServer(
            server -> {
              server
                  .bearerTokenResolver(tokens)
                  .authenticationEntryPoint(unauthenticated)
                  .accessDeniedHandler(denied);
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

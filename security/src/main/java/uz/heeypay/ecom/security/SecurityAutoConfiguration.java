package uz.heeypay.ecom.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import uz.heeypay.ecom.error.ProblemSecurityHandler;

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
      SecurityConstants.BOOT_RESOURCE_SERVER_SECURITY
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityProperties.class)
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
      BearerTokenResolver tokens) {
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
                  .forEach((route, role) -> routes.requestMatchers(route(route)).hasRole(role));
              routes.anyRequest().denyAll();
            })
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .oauth2ResourceServer(
            server ->
                server
                    .bearerTokenResolver(tokens)
                    .authenticationEntryPoint(problems)
                    .accessDeniedHandler(problems)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
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
}

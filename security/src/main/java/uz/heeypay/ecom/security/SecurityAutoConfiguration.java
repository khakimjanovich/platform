package uz.heeypay.ecom.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import uz.heeypay.ecom.error.ProblemSecurityHandler;

/**
 * Adding the library secures the app: every call carries a Keycloak access token except the
 * configured public routes, each listed route needs its role, anything else is refused, and 401 and
 * 403 answer with problem details. An app that needs more defines its own {@link
 * SecurityFilterChain}.
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
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      SecurityProperties properties,
      ProblemSecurityHandler problems,
      KeycloakJwtConverter converter) {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            routes -> {
              properties.publicRoutes().forEach(route -> matcher(routes, route).permitAll());
              properties.roles().forEach((route, role) -> matcher(routes, route).hasRole(role));
              routes.anyRequest().denyAll();
            })
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .oauth2ResourceServer(
            server ->
                server
                    .authenticationEntryPoint(problems)
                    .accessDeniedHandler(problems)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
        .build();
  }

  /** "POST /path/**" matches that method only; "/path/**" matches every method. */
  private static AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl matcher(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          routes,
      String route) {
    var parts = route.trim().split(SecurityConstants.METHOD_SEPARATOR, 2);
    return parts.length == 2
        ? routes.requestMatchers(HttpMethod.valueOf(parts[0]), parts[1].trim())
        : routes.requestMatchers(parts[0]);
  }
}

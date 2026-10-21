package uz.heeypay.ecom.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A module adds a filter to the one security chain with a {@code Customizer<HttpSecurity>} bean,
 * which Spring Security applies before the library's rules; the route rules still hold, even
 * against a customizer that tries to open a route.
 */
@SpringBootTest(
    classes = SecurityCustomizerTest.App.class,
    properties = "ecom.security.roles[/admin/**]=ADMIN")
class SecurityCustomizerTest {

  @Configuration
  @EnableAutoConfiguration
  static class App {

    @Bean
    JwtDecoder jwtDecoder() {
      return token -> {
        if (!token.equals("admin")) throw new BadJwtException("bad token");
        return Jwt.withTokenValue(token)
            .header("alg", "none")
            .claim("preferred_username", token)
            .claim("realm_access", Map.of("roles", List.of(token)))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
      };
    }

    /** What a module contributes: a filter right after the token check. */
    @Bean
    Customizer<HttpSecurity> moduleFilter() {
      return http ->
          http.addFilterAfter(
              new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(
                    jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    jakarta.servlet.FilterChain chain)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                  response.setHeader("X-Module", "seen");
                  chain.doFilter(request, response);
                }
              },
              BearerTokenAuthenticationFilter.class);
    }

    @RestController
    static class Endpoints {
      @GetMapping({"/admin/x", "/other"})
      String any() {
        return "ok";
      }
    }
  }

  @Autowired WebApplicationContext context;
  MockMvc mvc;

  @BeforeEach
  void mvc() {
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @Test
  void theModulesFilterRunsInTheChain() throws Exception {
    mvc.perform(get("/admin/x").header("Authorization", "Bearer admin"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Module", "seen"));
  }

  @Test
  void theRouteRulesStillApply() throws Exception {
    mvc.perform(get("/admin/x")).andExpect(status().isUnauthorized());
    mvc.perform(get("/other").header("Authorization", "Bearer admin"))
        .andExpect(status().isForbidden())
        .andExpect(header().string("X-Module", "seen"));
  }

  /** A module trying to open a route itself: the rules live in configuration only. */
  @Configuration
  static class RouteRule {
    @Bean
    Customizer<HttpSecurity> moduleRouteRule() {
      return http ->
          http.authorizeHttpRequests(routes -> routes.requestMatchers("/admin/**").permitAll());
    }
  }

  @Test
  void aCustomizersPermitAllDoesNotWin() {
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(App.class, RouteRule.class)
                    .properties("server.port=0", "ecom.security.roles[/admin/**]=ADMIN")
                    .run()
                    .close())
        .rootCause()
        .hasMessageContaining("ecom.security.roles");
  }
}

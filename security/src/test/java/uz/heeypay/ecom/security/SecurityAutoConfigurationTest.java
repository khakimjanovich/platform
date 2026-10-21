package uz.heeypay.ecom.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/** The route rules come from configuration, and everything not listed is refused. */
@SpringBootTest(
    classes = SecurityAutoConfigurationTest.App.class,
    properties = {
      "spring.config.import=classpath:properties/error-exception.yaml",
      "ecom.security.public-routes[0]=POST /signup",
      "ecom.security.public-routes[1]=/docs/**",
      "ecom.security.roles[/admin/**]=ADMIN",
      "ecom.security.roles[/me/**]=PARTNER"
    })
class SecurityAutoConfigurationTest {

  @Configuration
  @EnableAutoConfiguration
  static class App {

    /** "admin" and "partner" are tokens with that realm role; anything else is invalid. */
    @Bean
    JwtDecoder jwtDecoder() {
      return token -> {
        if (!List.of("admin", "partner").contains(token)) throw new BadJwtException("bad token");
        return Jwt.withTokenValue(token)
            .header("alg", "none")
            .claim("preferred_username", token)
            .claim("realm_access", Map.of("roles", List.of(token)))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
      };
    }

    @RestController
    static class Endpoints {
      @PostMapping("/signup")
      String signup() {
        return "ok";
      }

      @GetMapping({"/signup", "/admin/x", "/me/x", "/other", "/docs/x"})
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
  void publicRoutesNeedNoTokenAndMayBeLimitedToOneMethod() throws Exception {
    mvc.perform(post("/signup")).andExpect(status().isOk());
    mvc.perform(get("/docs/x")).andExpect(status().isOk());
    mvc.perform(get("/signup")).andExpect(status().isUnauthorized());
  }

  @Test
  void aPublicRouteIgnoresAStaleToken() throws Exception {
    mvc.perform(post("/signup").header("Authorization", "Bearer garbage"))
        .andExpect(status().isOk());
    mvc.perform(get("/docs/x").header("Authorization", "Bearer garbage"))
        .andExpect(status().isOk());
  }

  @Test
  void eachRouteNeedsItsRole() throws Exception {
    mvc.perform(get("/admin/x").header("Authorization", "Bearer admin")).andExpect(status().isOk());
    mvc.perform(get("/me/x").header("Authorization", "Bearer partner")).andExpect(status().isOk());
    mvc.perform(get("/admin/x").header("Authorization", "Bearer partner"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void noOrBadTokenIsA401Problem() throws Exception {
    mvc.perform(get("/admin/x"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mvc.perform(get("/admin/x").header("Authorization", "Bearer forged"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anythingNotListedIsRefused() throws Exception {
    mvc.perform(get("/other").header("Authorization", "Bearer admin"))
        .andExpect(status().isForbidden());
  }
}

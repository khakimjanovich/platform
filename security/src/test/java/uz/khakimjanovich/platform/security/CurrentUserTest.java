package uz.khakimjanovich.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void noAuthenticationGivesNull() {
    SecurityContextHolder.clearContext();
    assertThat(CurrentUser.name()).isNull();
    assertThat(CurrentUser.clientId()).isNull();
    assertThat(CurrentUser.id()).isNull();
  }

  @Test
  void theIdIsTheTokensSubject() {
    var jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject("4f9c2c1e-0000-4000-8000-000000000001")
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    assertThat(CurrentUser.id()).isEqualTo("4f9c2c1e-0000-4000-8000-000000000001");
  }

  @Test
  void aRealmRoleIsAskedForByItsKeycloakName() {
    var jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .claim("realm_access", Map.of("roles", List.of("merchant-admin")))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new KeycloakJwtConverter().convert(jwt));
    assertThat(CurrentUser.hasRealmRole("merchant-admin")).isTrue();
    assertThat(CurrentUser.hasRealmRole("admin")).isFalse();
    SecurityContextHolder.clearContext();
    assertThat(CurrentUser.hasRealmRole("merchant-admin")).isFalse();
  }
}

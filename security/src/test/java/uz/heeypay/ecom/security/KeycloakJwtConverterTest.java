package uz.heeypay.ecom.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtConverterTest {

  private final KeycloakJwtConverter converter = new KeycloakJwtConverter();

  @Test
  void realmRolesBecomeAuthoritiesAndTheUsernameIsTheName() {
    var auth =
        converter.convert(
            token(Map.of("realm_access", Map.of("roles", List.of("admin", "offline_access")))));
    assertThat(auth.getName()).isEqualTo("aziz");
    assertThat(auth.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN", "ROLE_OFFLINE_ACCESS");
  }

  @Test
  void aTokenWithoutRolesGrantsNothing() {
    assertThat(converter.convert(token(Map.of())).getAuthorities()).isEmpty();
  }

  private static Jwt token(Map<String, Object> claims) {
    var jwt =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .claim("preferred_username", "aziz")
            .issuedAt(Instant.now());
    claims.forEach(jwt::claim);
    return jwt.build();
  }

  @Test
  void aTokenWithoutAUsernameIsNamedByItsSubject() {
    var jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject("8b1f-service-account")
            .claim("realm_access", java.util.Map.of("roles", java.util.List.of("merchant")))
            .build();
    org.assertj.core.api.Assertions.assertThat(new KeycloakJwtConverter().convert(jwt).getName())
        .isEqualTo("8b1f-service-account");
  }
}

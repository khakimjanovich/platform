package uz.heeypay.ecom.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Without an authentication there is no caller: null, never a NullPointerException. */
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
}

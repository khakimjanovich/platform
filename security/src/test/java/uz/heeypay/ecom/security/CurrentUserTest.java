package uz.heeypay.ecom.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

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
  }
}

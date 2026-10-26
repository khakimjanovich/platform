package uz.heeypay.ecom.security;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Who is calling, from the access token: a person's name (an admin's username or a partner's email)
 * and Keycloak id, or the API client a merchant's server uses. Each is {@code null} without a
 * token.
 */
@UtilityClass
public class CurrentUser {

  /** The caller's name, or {@code null} without an authentication. */
  public static @Nullable String name() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null ? null : authentication.getName();
  }

  /** The caller's Keycloak id (the token's subject), or {@code null} without a token. */
  public static @Nullable String id() {
    return SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken token
        ? token.getToken().getSubject()
        : null;
  }

  /** The API client's id, or {@code null} without a token. */
  public static @Nullable String clientId() {
    return SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken token
        ? token.getToken().getClaimAsString(SecurityConstants.CLIENT_CLAIM)
        : null;
  }
}

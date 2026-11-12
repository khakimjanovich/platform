package uz.khakimjanovich.platform.security;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@UtilityClass
public class CurrentUser {

  public static @Nullable String name() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null ? null : authentication.getName();
  }

  public static @Nullable String id() {
    return SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken token
        ? token.getToken().getSubject()
        : null;
  }

  public static @Nullable String clientId() {
    return SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken token
        ? token.getToken().getClaimAsString(SecurityConstants.CLIENT_CLAIM)
        : null;
  }

  public static boolean hasRealmRole(String realmRole) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) return false;
    var wanted = KeycloakJwtConverter.authority(realmRole);
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> wanted.equals(authority.getAuthority()));
  }
}

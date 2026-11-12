package uz.khakimjanovich.platform.security;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    var username = jwt.getClaimAsString(SecurityConstants.USERNAME_CLAIM);
    // Without a username the token's subject names the caller, as Spring Security does.
    return username == null
        ? new JwtAuthenticationToken(jwt, roles(jwt))
        : new JwtAuthenticationToken(jwt, roles(jwt), username);
  }

  private static Collection<GrantedAuthority> roles(Jwt jwt) {
    Map<String, Object> realmAccess =
        Optional.ofNullable(jwt.getClaimAsMap(SecurityConstants.REALM_ACCESS_CLAIM))
            .orElse(Map.of());
    if (!(realmAccess.get(SecurityConstants.ROLES) instanceof List<?> roles)) {
      return List.of();
    }
    return roles.stream()
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(authority(role.toString())))
        .toList();
  }

  static String authority(String realmRole) {
    return SecurityConstants.ROLE_PREFIX + realmRole.toUpperCase(Locale.ROOT);
  }
}

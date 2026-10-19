package uz.heeypay.ecom.security;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityConstants {

  public static final String PROPERTIES_PREFIX = "ecom.security";

  /* ------- Keycloak token ------- */
  public static final String USERNAME_CLAIM = "preferred_username";

  /** The client the token was issued to; for an API client, its client id. */
  public static final String CLIENT_CLAIM = "azp";

  public static final String REALM_ACCESS_CLAIM = "realm_access";
  public static final String ROLES = "roles";
  public static final String ROLE_PREFIX = "ROLE_";

  /** A rule may start with an HTTP method: "POST /api/v1/registrations/**". */
  public static final String METHOD_SEPARATOR = " ";

  /* ------- Boot's own security chains, which ours replaces ------- */
  public static final String BOOT_WEB_SECURITY =
      "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration";
  public static final String BOOT_MANAGEMENT_SECURITY =
      "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration";
  public static final String BOOT_RESOURCE_SERVER_SECURITY =
      "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration";
}

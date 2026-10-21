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

  /* ------- service-to-service tokens ------- */
  public static final String SERVICE_TOKENS_PREFIX = "ecom.security.service-tokens";

  /** Whose token a group's calls carry: the service's own, one per registration. */
  public static final String SERVICE_PRINCIPAL = "service-tokens";

  public static final String SERVICE_AUTHORITY = "ROLE_SERVICE";
  public static final String BOOT_OAUTH2_CLIENT =
      "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration";
  public static final String CLIENT_REGISTRATIONS =
      "org.springframework.security.oauth2.client.registration.ClientRegistrationRepository";
  public static final String SERVICE_GROUP_CONFIGURER =
      "org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer";

  /* ------- Boot's own security chains, which ours replaces ------- */
  public static final String BOOT_WEB_SECURITY =
      "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration";
  public static final String BOOT_MANAGEMENT_SECURITY =
      "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration";

  /** With service tokens an app has OAuth2 clients, but never Boot's login page. */
  public static final String BOOT_OAUTH2_CLIENT_SECURITY =
      "org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration";

  public static final String BOOT_RESOURCE_SERVER_SECURITY =
      "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration";
}

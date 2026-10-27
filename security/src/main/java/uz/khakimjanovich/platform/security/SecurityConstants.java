package uz.khakimjanovich.platform.security;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityConstants {

  public static final String PROPERTIES_PREFIX = "platform.security";

  /* ------- Keycloak token ------- */
  public static final String USERNAME_CLAIM = "preferred_username";

  /** The client the token was issued to; for an API client, its client id. */
  public static final String CLIENT_CLAIM = "azp";

  public static final String REALM_ACCESS_CLAIM = "realm_access";
  public static final String ROLES = "roles";
  public static final String ROLE_PREFIX = "ROLE_";

  public static final String ROLE_MISSING = "Route %s needs at least one realm role, none blank";

  /**
   * A trusted issuer without its URI, audience, clients or roles would accept or refuse silently.
   */
  public static final String ISSUER_INCOMPLETE =
      "Trusted issuer %s needs its uri, audience, at least one client and the roles it may grant";

  public static final String ISSUER_NOT_TRUSTED = "The token's issuer is not trusted";

  /** One client of one issuer listed in two entries: which roles it may hold would be unclear. */
  public static final String ISSUER_CLIENT_TWICE = "Client %s of trusted issuer %s is listed twice";

  /**
   * One issuer's entries naming different audiences: its tokens can name only one this API takes.
   */
  public static final String ISSUER_AUDIENCES = "Trusted issuer %s names more than one audience";

  /** Trusted issuers replace Boot's one issuer: both set, one would be ignored. */
  public static final String ISSUERS_AND_ISSUER_URI =
      "Set platform.security.issuers or spring.security.oauth2.resourceserver.jwt.issuer-uri, not both";

  public static final String BOOT_ISSUER_URI =
      "spring.security.oauth2.resourceserver.jwt.issuer-uri";

  /** A rule may start with an HTTP method: "POST /api/v1/registrations/**". */
  public static final String METHOD_SEPARATOR = " ";

  /* ------- service-to-service tokens ------- */
  public static final String SERVICE_TOKENS_PREFIX = "platform.security.service-tokens";
  public static final String ROUTES_IN_CONFIGURATION =
      "A Customizer<HttpSecurity> set route rules; they belong in platform.security.roles and"
          + " platform.security.public-routes, and a customizer adds filters only";
  public static final String HTTP_CLIENT_FACTORY =
      "org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder";
  public static final String DEFAULT_TOKEN_CONNECT_TIMEOUT = "5s";
  public static final String DEFAULT_TOKEN_READ_TIMEOUT = "10s";
  public static final String INVALID_TOKEN = "error=\"invalid_token\"";

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

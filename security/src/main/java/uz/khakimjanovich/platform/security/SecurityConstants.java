package uz.khakimjanovich.platform.security;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityConstants {

  public static final String PROPERTIES_PREFIX = "platform.security";

  /* ------- Keycloak token ------- */
  public static final String USERNAME_CLAIM = "preferred_username";

  public static final String CLIENT_CLAIM = "azp";

  public static final String REALM_ACCESS_CLAIM = "realm_access";
  public static final String ROLES = "roles";
  public static final String ROLE_PREFIX = "ROLE_";

  public static final String ROLE_MISSING = "Route %s needs at least one realm role, none blank";

  public static final String ISSUER_INCOMPLETE =
      "Trusted issuer %s needs its uri, audience and the roles it may grant";

  public static final String ISSUER_NOT_TRUSTED = "The token's issuer is not trusted";

  public static final String ISSUER_TWICE = "Trusted issuer %s is listed twice";

  public static final String ISSUERS_AND_ISSUER_URI =
      "Set platform.security.issuers or spring.security.oauth2.resourceserver.jwt.issuer-uri, not both";

  public static final String SSL_BUNDLE_WITHOUT_ISSUERS =
      "platform.security.ssl-bundle needs platform.security.issuers";

  public static final String BOOT_ISSUER_URI =
      "spring.security.oauth2.resourceserver.jwt.issuer-uri";

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

  public static final String BOOT_OAUTH2_CLIENT_SECURITY =
      "org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration";

  public static final String BOOT_RESOURCE_SERVER_SECURITY =
      "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration";

  static final String HTTPS = "https";

  static final String HTTP = "http";

  static final int HTTPS_PORT = 443;

  static final int HTTP_PORT = 80;
}

package uz.heeypay.ecom.keycloak;

import lombok.experimental.UtilityClass;

/** Keycloak admin REST paths and representation fields. */
@UtilityClass
public class KeycloakConstants {

  public static final String PROPERTIES_PREFIX = "ecom.keycloak";
  public static final String REGISTRATION_ID = "keycloak-admin";
  public static final String ADMIN_PATH = "/admin/realms/%s";
  public static final String TOKEN_PATH = "/realms/%s/protocol/openid-connect/token";

  /* ------- paths, relative to the realm ------- */
  public static final String USERS = "/users";
  public static final String USER = "/users/{id}";
  public static final String USER_REALM_ROLES = "/users/{id}/role-mappings/realm";
  public static final String ROLE = "/roles/{name}";
  public static final String CLIENTS = "/clients";
  public static final String CLIENT_SECRET = "/clients/{id}/client-secret";

  /** The observability library's HttpClientLogging.NO_BODIES: the secret never reaches a log. */
  public static final String NO_LOGGED_BODIES = "ecom.http-client-logging.no-bodies";

  public static final String CLIENT_SERVICE_ACCOUNT = "/clients/{id}/service-account-user";
  public static final String ID = "id";

  /* ------- Keycloak's own error bodies ------- */
  public static final String ERROR_MESSAGE = "errorMessage";
  public static final String ERROR = "error";
  public static final String MISSING_FIELD = "Keycloak answered without %s";
  public static final String MISSING_LOCATION = "Keycloak answered a create without a Location";

  public static final String DEFAULT_CONNECT_TIMEOUT = "5s";
  public static final String DEFAULT_READ_TIMEOUT = "10s";

  /* ------- representation fields ------- */
  public static final String USERNAME = "username";
  public static final String EMAIL = "email";
  public static final String ENABLED = "enabled";
  public static final String EMAIL_VERIFIED = "emailVerified";
  public static final String CREDENTIALS = "credentials";
  public static final String TYPE = "type";
  public static final String VALUE = "value";
  public static final String TEMPORARY = "temporary";
  public static final String PASSWORD = "password";
  public static final String CLIENT_ID = "clientId";
  public static final String PUBLIC_CLIENT = "publicClient";
  public static final String SERVICE_ACCOUNTS_ENABLED = "serviceAccountsEnabled";
  public static final String STANDARD_FLOW_ENABLED = "standardFlowEnabled";
  public static final String DIRECT_ACCESS_GRANTS_ENABLED = "directAccessGrantsEnabled";
}

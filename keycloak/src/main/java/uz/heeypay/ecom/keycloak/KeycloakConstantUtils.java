package uz.heeypay.ecom.keycloak;

import lombok.experimental.UtilityClass;

/** Keycloak admin REST paths and representation fields. */
@UtilityClass
public class KeycloakConstantUtils {

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
  public static final String CLIENT_SERVICE_ACCOUNT = "/clients/{id}/service-account-user";
  public static final String ID = "id";

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

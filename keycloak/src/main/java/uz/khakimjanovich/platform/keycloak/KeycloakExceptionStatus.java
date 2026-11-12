package uz.khakimjanovich.platform.keycloak;

import uz.khakimjanovich.platform.error.StatusThrowable;

public enum KeycloakExceptionStatus implements StatusThrowable {
  KEYCLOAK_CONFLICT,
  KEYCLOAK_REJECTED,
  KEYCLOAK_UNAVAILABLE;

  @Override
  public String getType() {
    return name();
  }
}

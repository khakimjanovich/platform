package uz.heeypay.ecom.keycloak;

import uz.heeypay.ecom.error.StatusThrowable;

/** What can go wrong talking to Keycloak's admin API. */
public enum KeycloakExceptionStatus implements StatusThrowable {
  /** Keycloak already has what we tried to create, e.g. a user with this username. */
  KEYCLOAK_CONFLICT,
  /** Keycloak refused or didn't answer: any other error status, a timeout, no token. */
  KEYCLOAK_UNAVAILABLE;

  @Override
  public String getType() {
    return name();
  }
}

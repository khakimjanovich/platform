package uz.heeypay.ecom.keycloak;

/** Keycloak already has what we tried to create, e.g. a user with this username. */
public class KeycloakConflictException extends RuntimeException {

  public KeycloakConflictException(String what) {
    super(what);
  }
}

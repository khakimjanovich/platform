package uz.khakimjanovich.platform.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/** The parts of Keycloak's admin API representations that {@link KeycloakAdmin} sends and reads. */
@UtilityClass
class KeycloakRepresentations {

  /** A new user; Keycloak's UserRepresentation. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record User(
      String username,
      String email,
      @Nullable String firstName,
      boolean enabled,
      boolean emailVerified,
      List<String> requiredActions,
      @Nullable List<Credential> credentials) {}

  /** A password; Keycloak's CredentialRepresentation. */
  record Credential(String type, String value, boolean temporary) {}

  /** A user update that changes only {@code enabled}. */
  record Enabled(boolean enabled) {}

  /** A new confidential client; Keycloak's ClientRepresentation. */
  record Client(
      String clientId,
      boolean enabled,
      boolean publicClient,
      boolean serviceAccountsEnabled,
      boolean standardFlowEnabled,
      boolean directAccessGrantsEnabled) {}

  /** A realm role, as a role mapping takes it. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Role(@Nullable String id, @Nullable String name) {}

  /** Anything with an id: the service account's user. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Identified(@Nullable String id) {}

  /** A client's secret. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Secret(@Nullable String value) {}

  /** Keycloak's error: {@code errorMessage} (admin API) or {@code error} (token endpoint). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Failure(@Nullable String errorMessage, @Nullable String error) {}
}

package uz.khakimjanovich.platform.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

@UtilityClass
class KeycloakRepresentations {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record User(
      String username,
      String email,
      @Nullable String firstName,
      boolean enabled,
      boolean emailVerified,
      List<String> requiredActions,
      @Nullable List<Credential> credentials) {}

  record Credential(String type, String value, boolean temporary) {}

  record Enabled(boolean enabled) {}

  record Client(
      String clientId,
      boolean enabled,
      boolean publicClient,
      boolean serviceAccountsEnabled,
      boolean standardFlowEnabled,
      boolean directAccessGrantsEnabled) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Role(@Nullable String id, @Nullable String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Identified(@Nullable String id) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Secret(@Nullable String value) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Failure(@Nullable String errorMessage, @Nullable String error) {}
}

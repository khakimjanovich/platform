package uz.heeypay.ecom.vault;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** Reads key-value secrets from Vault. Writing them is the bank's job, outside the app. */
@RequiredArgsConstructor
public class VaultSecrets {

  private final RestClient vault;
  private final String mount;

  /** The secret, or empty if there is none at this path. */
  public Optional<Map<String, String>> get(String path) {
    try {
      var values =
          vault
              .get()
              .uri(VaultConstants.DATA_PATH, mount, path)
              .retrieve()
              .body(JsonNode.class)
              .path(VaultConstants.DATA)
              .path(VaultConstants.DATA);
      var secret = new LinkedHashMap<String, String>();
      values.properties().forEach(e -> secret.put(e.getKey(), e.getValue().asString()));
      return Optional.of(secret);
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) return Optional.empty();
      throw new VaultUnavailableException(e);
    } catch (RestClientException e) {
      throw new VaultUnavailableException(e);
    }
  }
}

package uz.khakimjanovich.platform.vault;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.web.client.RestClientException;

public class VaultSecrets {

  private final VaultVersionedKeyValueOperations kv;

  public VaultSecrets(VaultTemplate vault, String mount) {
    this.kv = vault.opsForVersionedKeyValue(mount);
  }

  public Optional<Map<String, String>> get(String path) {
    try {
      var versioned = kv.get(path);
      if (versioned == null || !versioned.hasData()) return Optional.empty();
      var secret = new LinkedHashMap<String, String>();
      versioned.getRequiredData().forEach((key, value) -> secret.put(key, String.valueOf(value)));
      return Optional.of(secret);
    } catch (VaultException | RestClientException e) {
      // VaultException when Vault answers with an error; RestClientException (connection refused,
      // a timeout) when it doesn't answer at all.
      throw new VaultUnavailableException(e);
    }
  }
}

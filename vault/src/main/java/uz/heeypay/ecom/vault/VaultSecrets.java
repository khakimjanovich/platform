package uz.heeypay.ecom.vault;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;

/**
 * Reads key-value secrets (KV version 2, the latest version) from Vault. Writing them is the bank's
 * job, outside the app.
 */
public class VaultSecrets {

  private final VaultVersionedKeyValueOperations kv;

  public VaultSecrets(VaultTemplate vault, String mount) {
    this.kv = vault.opsForVersionedKeyValue(mount);
  }

  /**
   * The secret, or empty if there is none at this path. Throws {@link VaultUnavailableException} if
   * Vault can't be reached or refuses, so callers fail closed.
   */
  public Optional<Map<String, String>> get(String path) {
    try {
      var versioned = kv.get(path);
      if (versioned == null || !versioned.hasData()) return Optional.empty();
      var secret = new LinkedHashMap<String, String>();
      versioned.getRequiredData().forEach((key, value) -> secret.put(key, String.valueOf(value)));
      return Optional.of(secret);
    } catch (VaultException e) {
      throw new VaultUnavailableException(e);
    }
  }
}

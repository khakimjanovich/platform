package uz.heeypay.ecom.vault;

import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * Each merchant API client's request-signing key, put into Vault by the bank under {@code
 * merchants/<client id>} (e.g. {@code vault kv put secret/merchants/<client id>
 * signingKey=<base64>}). The app never creates or shows one. Every module that checks or makes a
 * signature reads it here, so the key never travels between modules.
 */
@RequiredArgsConstructor
public class SigningKeys {

  private final VaultSecrets vault;

  /**
   * The client's key, or empty if the bank hasn't set one. Throws {@link VaultUnavailableException}
   * if Vault can't be reached, so callers fail closed.
   */
  public Optional<byte[]> find(String clientId) {
    return vault
        .get(VaultConstants.SIGNING_KEY_PATH.formatted(clientId))
        .map(secret -> secret.get(VaultConstants.SIGNING_KEY))
        .map(Base64.getDecoder()::decode);
  }
}

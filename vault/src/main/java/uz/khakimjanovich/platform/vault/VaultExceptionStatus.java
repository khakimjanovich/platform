package uz.khakimjanovich.platform.vault;

import uz.khakimjanovich.platform.error.StatusThrowable;

/**
 * Stable error codes for Vault Transit. Unavailable: Vault didn't answer, failed or refused the
 * app's token, so a later call may work. Refused: Vault answered that this call can't work (a
 * ciphertext that doesn't open with its context, a tampered one, a key that doesn't exist).
 */
public enum VaultExceptionStatus implements StatusThrowable {
  VAULT_TRANSIT_UNAVAILABLE,
  VAULT_TRANSIT_REFUSED;

  @Override
  public String getType() {
    return name();
  }
}

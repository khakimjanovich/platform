package uz.khakimjanovich.platform.vault;

import uz.khakimjanovich.platform.error.StatusThrowable;

public enum VaultExceptionStatus implements StatusThrowable {
  VAULT_TRANSIT_UNAVAILABLE,
  VAULT_TRANSIT_REFUSED;

  @Override
  public String getType() {
    return name();
  }
}

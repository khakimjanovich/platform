package uz.heeypay.ecom.vault;

/** Vault didn't answer, or answered with an error: callers must fail closed. */
public class VaultUnavailableException extends RuntimeException {

  public VaultUnavailableException(Throwable cause) {
    super("Vault is not available", cause);
  }
}

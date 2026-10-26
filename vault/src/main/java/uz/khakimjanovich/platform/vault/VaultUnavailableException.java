package uz.khakimjanovich.platform.vault;

import java.io.Serial;

/** Vault didn't answer, or answered with an error: callers must fail closed. */
public class VaultUnavailableException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public VaultUnavailableException(Throwable cause) {
    super("Vault is not available", cause);
  }
}

package uz.heeypay.ecom.error;

import lombok.Getter;

/**
 * A failure the client should see. The HTTP status and the message in the request's language
 * ({@code Accept-Language}) come from the status's definition; {@code params} fill its {0}, {1}…
 */
@Getter
public class GeneralException extends RuntimeException {

  private final StatusThrowable status;
  private final transient Object[] params;

  public GeneralException(StatusThrowable status, Object... params) {
    super(status.getType());
    this.status = status;
    this.params = params;
  }
}

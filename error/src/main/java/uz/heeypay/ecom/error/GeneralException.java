package uz.heeypay.ecom.error;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * A failure the client should see. The HTTP status and the message in the request's language
 * ({@code Accept-Language}) come from the status's definition; {@code params} fill its {0}, {1}…
 * Sub-errors carry a third party's own codes and messages (a card scheme's), for programs to read.
 * A limit that lifts by itself says when ({@link #retryAfter(Duration)}).
 */
@Getter
public class GeneralException extends RuntimeException {

  private final StatusThrowable status;
  private final transient Object[] params;
  private final List<SubError> subErrors = new ArrayList<>();
  private @Nullable Duration retryAfter;

  public GeneralException(StatusThrowable status, Object... params) {
    super(status.getType());
    this.status = status;
    this.params = params;
  }

  public GeneralException subError(@Nullable String code, @Nullable String message) {
    subErrors.add(new SubError(code, message));
    return this;
  }

  /** When the caller may try again: sent as {@code Retry-After} (RFC 6585 §4, RFC 9110 §10.2.3). */
  public GeneralException retryAfter(Duration wait) {
    this.retryAfter = wait;
    return this;
  }

  /** A third party's own code and message, as received. */
  public record SubError(@Nullable String code, @Nullable String message) {}
}

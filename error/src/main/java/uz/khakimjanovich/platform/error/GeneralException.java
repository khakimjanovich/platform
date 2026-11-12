package uz.khakimjanovich.platform.error;

import java.io.Serial;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class GeneralException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  // An exception answers the request it was thrown in; it is never serialized.
  private final transient StatusThrowable status;
  private final transient Object[] params;
  private final transient List<SubError> subErrors = new ArrayList<>();
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

  public GeneralException retryAfter(Duration wait) {
    this.retryAfter = wait;
    return this;
  }

  public record SubError(@Nullable String code, @Nullable String message) {}
}

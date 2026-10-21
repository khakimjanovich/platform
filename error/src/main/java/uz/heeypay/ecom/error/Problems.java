package uz.heeypay.ecom.error;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.RestClientResponseException;

/**
 * Another service's problem detail, read from its error response: its stable {@code code} and a
 * third party's own codes ({@code subErrors}). A body that isn't a problem (an HTML error page, no
 * body at all) is no problem: nobody decided, and the caller treats it as such.
 */
public final class Problems {

  private Problems() {}

  /** The problem the other service answered, or empty if its body isn't one. */
  public static Optional<ProblemDetail> of(RestClientResponseException e) {
    try {
      return Optional.ofNullable(e.getResponseBodyAs(ProblemDetail.class));
    } catch (RuntimeException notAProblem) {
      return Optional.empty();
    }
  }

  /** Its stable code, e.g. {@code SV_GATE_ERROR}, or null if it has none. */
  public static String code(ProblemDetail problem) {
    return problem.getProperties() == null
            || !(problem.getProperties().get(ErrorConstants.CODE_PROPERTY) instanceof String code)
        ? null
        : code;
  }

  /** A third party's own codes and messages, as the other service passed them on. */
  public static List<GeneralException.SubError> subErrors(ProblemDetail problem) {
    if (problem.getProperties() == null
        || !(problem.getProperties().get(ErrorConstants.SUB_ERRORS_PROPERTY)
            instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(
            entry ->
                new GeneralException.SubError(
                    text(entry.get(ErrorConstants.CODE_PROPERTY)),
                    text(entry.get(ErrorConstants.MESSAGE))))
        .toList();
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }
}

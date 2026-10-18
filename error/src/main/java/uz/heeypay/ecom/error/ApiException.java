package uz.heeypay.ecom.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A failure the client should see: an HTTP status and a stable error code. The readable {@code
 * detail} is the message with the same key as the code, in the request's language ({@code
 * Accept-Language}), filled with {@code args}.
 */
public class ApiException extends ErrorResponseException {

  public ApiException(HttpStatusCode status, String code, Object... args) {
    super(status, problem(status, code), null, code, args);
  }

  private static ProblemDetail problem(HttpStatusCode status, String code) {
    var problem = ProblemDetail.forStatus(status);
    problem.setProperty(ErrorConstantUtils.CODE_PROPERTY, code);
    return problem;
  }
}

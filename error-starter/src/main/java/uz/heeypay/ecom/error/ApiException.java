package uz.heeypay.ecom.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A failure the client should see: an HTTP status, a stable error code, and a readable detail. */
public class ApiException extends ErrorResponseException {

  public ApiException(HttpStatusCode status, String code, String detail) {
    super(status, problem(status, code, detail), null);
  }

  private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    return problem;
  }
}

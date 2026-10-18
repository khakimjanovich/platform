package uz.heeypay.ecom.error;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Spring's own handler turns every {@link ApiException} and every framework error into a problem detail.
 * Anything unexpected becomes a plain 500 with a code, never a stack trace.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Log log = LogFactory.getLog(ProblemDetailsAdvice.class);

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception ex) {
    log.error(ErrorStarterConstantUtils.LOG_UNEXPECTED_ERROR, ex);
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ErrorStarterConstantUtils.INTERNAL_ERROR_DETAIL);
    problem.setProperty(ErrorStarterConstantUtils.CODE_PROPERTY, ErrorStarterConstantUtils.INTERNAL_ERROR_CODE);
    return problem;
  }
}

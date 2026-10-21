package uz.heeypay.ecom.error;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Method security ({@code @PreAuthorize}) refusing a call inside a controller is a 403 like the
 * security chain's, not an unexpected 500. Checked before {@link ProblemDetailsAdvice}'s catch-all.
 * Its condition also holds when an app scans this package: without Spring Security it is skipped.
 */
@RestControllerAdvice
@ConditionalOnClass(name = ErrorConstants.ACCESS_DENIED)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
class AccessDeniedAdvice {

  private final ProblemDetailsAdvice problems;

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException ex) {
    return problems.general(new GeneralException(ErrorExceptionStatus.FORBIDDEN));
  }
}

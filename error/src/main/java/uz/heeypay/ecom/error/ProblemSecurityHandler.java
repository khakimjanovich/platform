package uz.heeypay.ecom.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 401 and 403 from Spring Security become the same problem details as every other error. They are
 * handed to Spring MVC's exception resolver, so they are written exactly like a {@link
 * GeneralException} from a controller.
 */
@RequiredArgsConstructor
public class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final HandlerExceptionResolver resolver;

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, ErrorConstantUtils.BEARER_CHALLENGE);
    resolver.resolveException(
        request, response, null, new GeneralException(ErrorExceptionStatus.UNAUTHORIZED));
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
    resolver.resolveException(
        request, response, null, new GeneralException(ErrorExceptionStatus.FORBIDDEN));
  }
}

package uz.heeypay.ecom.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.HandlerExceptionResolver;

class ProblemSecurityHandlerTest {

  private ApiException resolved;
  private final HandlerExceptionResolver resolver =
      (request, response, handler, ex) -> {
        resolved = (ApiException) ex;
        return null;
      };
  private final ProblemSecurityHandler handler = new ProblemSecurityHandler(resolver);

  @Test
  void noValidTokenIsA401ProblemWithABearerChallenge() {
    var response = new MockHttpServletResponse();
    handler.commence(new MockHttpServletRequest(), response, new BadCredentialsException("expired"));

    assertThat(resolved.getStatusCode().value()).isEqualTo(401);
    assertThat(resolved.getBody().getProperties()).containsEntry("code", "UNAUTHORIZED");
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
  }

  @Test
  void theWrongRoleIsA403Problem() {
    handler.handle(new MockHttpServletRequest(), new MockHttpServletResponse(), new AccessDeniedException("no role"));

    assertThat(resolved.getStatusCode().value()).isEqualTo(403);
    assertThat(resolved.getBody().getProperties()).containsEntry("code", "FORBIDDEN");
  }
}

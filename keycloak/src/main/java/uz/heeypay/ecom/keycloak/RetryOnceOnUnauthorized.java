package uz.heeypay.ecom.keycloak;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * A 401 for a refused token (no challenge, or {@code error="invalid_token"}): the login interceptor
 * has dropped it, so the call is tried once more with a new one. Any other 401 ({@code
 * insufficient_scope}) is the answer. The security library's service tokens do the same; this
 * library doesn't depend on it.
 */
class RetryOnceOnUnauthorized implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    var response = execution.execute(request, body);
    if (!response.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) return response;
    var challenge = response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
    if (challenge != null && !challenge.contains(KeycloakConstants.INVALID_TOKEN)) return response;
    response.close();
    return execution.execute(request, body);
  }
}

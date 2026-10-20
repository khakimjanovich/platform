package uz.heeypay.ecom.observability;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Every call this service makes, in three readable lines at most: what went out ({@code →}), what
 * came back ({@code ←}) with its status and time, or why it failed ({@code ✗}). Bodies are masked
 * ({@link BodyMasking}); headers are never logged. A 4xx or 5xx answer is a warning. Every
 * RestClient gets it; a client built on a ClientHttpRequestFactory (a SOAP one) adds it itself.
 */
public class HttpClientLogging implements ClientHttpRequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(HttpClientLogging.class);

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    var call = request.getMethod() + " " + request.getURI();
    log.info("→ {} | {}", call, BodyMasking.masked(body, request.getHeaders().getContentType()));
    var started = System.nanoTime();
    ClientHttpResponse response;
    try {
      response = execution.execute(request, body);
    } catch (IOException e) {
      log.warn("✗ {} | {} ms | {}", call, millis(started), e.toString());
      throw e;
    }
    var bytes = response.getBody().readAllBytes();
    var status = response.getStatusCode();
    var line = "← {} {} | {} ms | {}";
    var masked = BodyMasking.masked(bytes, response.getHeaders().getContentType());
    if (status.isError()) log.warn(line, status.value(), call, millis(started), masked);
    else log.info(line, status.value(), call, millis(started), masked);
    return new Read(response, bytes);
  }

  private static long millis(long started) {
    return (System.nanoTime() - started) / 1_000_000;
  }

  /** The response, its body already read for the log and read again by the caller. */
  private record Read(ClientHttpResponse response, byte[] body) implements ClientHttpResponse {

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
      return response.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
      return response.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
      return response.getHeaders();
    }

    @Override
    public InputStream getBody() {
      return new ByteArrayInputStream(body);
    }

    @Override
    public void close() {
      response.close();
    }
  }
}

package uz.heeypay.ecom.observability;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import uz.heeypay.ecom.masking.Masking;

/**
 * Every call this service makes, in three readable lines at most: what went out ({@code →}), what
 * came back ({@code ←}) with its status and time, or why it failed ({@code ✗}). The URI and bodies
 * are masked ({@link Masking}); headers are never logged. A 4xx or 5xx answer is a warning. Only a
 * JSON, XML or form answer of up to {@value #MAX_LOGGED_BYTES} bytes is read for the log; anything
 * else is logged by its size and streams through untouched. Every RestClient gets it; a client
 * built on a ClientHttpRequestFactory (a SOAP one) adds it itself.
 */
public class HttpClientLogging implements ClientHttpRequestInterceptor {

  static final int MAX_LOGGED_BYTES = 4_000;

  private static final Logger log = LoggerFactory.getLogger(HttpClientLogging.class);

  private final Masking masking;

  public HttpClientLogging(Masking masking) {
    this.masking = masking;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    var call = request.getMethod() + " " + masking.uri(request.getURI());
    log.info("→ {} | {}", call, masking.body(body, type(request.getHeaders())));
    var started = System.nanoTime();
    ClientHttpResponse response;
    try {
      response = execution.execute(request, body);
    } catch (IOException e) {
      log.warn("✗ {} | {} ms | {}", call, millis(started), e.toString());
      throw e;
    }
    var headers = response.getHeaders();
    var length = headers.getContentLength();
    String logged;
    ClientHttpResponse answer = response;
    if (!loggable(headers.getContentType()) || length > MAX_LOGGED_BYTES) {
      logged = size(length, headers);
    } else {
      // Read at most one byte past the cap: a longer body is put back in front of the rest.
      var start = response.getBody().readNBytes(MAX_LOGGED_BYTES + 1);
      if (start.length <= MAX_LOGGED_BYTES) {
        logged = masking.body(start, type(headers));
        answer = new Read(response, new ByteArrayInputStream(start));
      } else {
        logged = "<more than " + MAX_LOGGED_BYTES + " bytes, " + headers.getContentType() + ">";
        answer =
            new Read(
                response,
                new SequenceInputStream(new ByteArrayInputStream(start), response.getBody()));
      }
    }
    var status = response.getStatusCode();
    var line = "← {} {} | {} ms | {}";
    if (status.isError()) log.warn(line, status.value(), call, millis(started), logged);
    else log.info(line, status.value(), call, millis(started), logged);
    return answer;
  }

  /** Bodies the masking can read: JSON, XML and forms. */
  private static boolean loggable(@Nullable MediaType type) {
    if (type == null) return false;
    var subtype = type.getSubtype().toLowerCase(Locale.ROOT);
    return subtype.contains("json")
        || subtype.contains("xml")
        || MediaType.APPLICATION_FORM_URLENCODED.equalsTypeAndSubtype(type);
  }

  private static String size(long length, HttpHeaders headers) {
    var type = headers.getContentType();
    return "<" + (length < 0 ? "?" : length) + " bytes" + (type == null ? "" : ", " + type) + ">";
  }

  private static @Nullable String type(HttpHeaders headers) {
    var type = headers.getContentType();
    return type == null ? null : type.toString();
  }

  private static long millis(long started) {
    return (System.nanoTime() - started) / 1_000_000;
  }

  /** The response, with the part already read for the log put back in front of its body. */
  private record Read(ClientHttpResponse response, InputStream body) implements ClientHttpResponse {

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
      return body;
    }

    @Override
    public void close() {
      response.close();
    }
  }
}

package uz.heeypay.ecom.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/**
 * The configured HTTP service groups call with a client-credentials token of their registration:
 * Spring Security's own interceptor gets and refreshes it. A 401 from the callee drops the token
 * (Spring's authorization-failure handler) and the call is tried once more with a new one.
 *
 * <p>Generated client interfaces can't carry {@code @ClientRegistrationId}, so a group names its
 * registration in {@code ecom.security.service-tokens.groups}. The token is the service's own,
 * never the caller's: every call of a group uses one authorized client.
 */
public class ServiceTokens implements RestClientHttpServiceGroupConfigurer {

  private final OAuth2AuthorizedClientManager manager;
  private final OAuth2AuthorizedClientService clients;
  private final Map<String, String> groups;

  public ServiceTokens(
      OAuth2AuthorizedClientManager manager,
      OAuth2AuthorizedClientService clients,
      Map<String, String> groups) {
    this.manager = manager;
    this.clients = clients;
    this.groups = groups;
  }

  @Override
  public void configureGroups(Groups<RestClient.Builder> services) {
    services.forEachClient(
        (group, builder) -> {
          var registration = groups.get(group.name());
          if (registration != null) builder.requestInterceptors(add(interceptor(registration)));
        });
  }

  private OAuth2ClientHttpRequestInterceptor interceptor(String registration) {
    var service =
        new AnonymousAuthenticationToken(
            SecurityConstants.SERVICE_PRINCIPAL,
            registration,
            List.of(new SimpleGrantedAuthority(SecurityConstants.SERVICE_AUTHORITY)));
    var tokens = new OAuth2ClientHttpRequestInterceptor(manager);
    tokens.setClientRegistrationIdResolver(request -> registration);
    tokens.setPrincipalResolver(request -> service);
    tokens.setAuthorizationFailureHandler(
        OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(clients));
    return tokens;
  }

  /** Retry once, then the token: a retried call goes through the token interceptor again. */
  private static java.util.function.Consumer<List<ClientHttpRequestInterceptor>> add(
      OAuth2ClientHttpRequestInterceptor tokens) {
    return interceptors -> {
      interceptors.add(new RetryOnceOnUnauthorized());
      interceptors.add(tokens);
    };
  }

  /** A 401 means the token was refused; the token interceptor has dropped it, so try once more. */
  static class RetryOnceOnUnauthorized implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      var response = execution.execute(request, body);
      if (!response.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) return response;
      response.close();
      return execution.execute(request, body);
    }
  }
}

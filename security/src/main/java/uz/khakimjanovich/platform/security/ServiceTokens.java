package uz.khakimjanovich.platform.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
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

  OAuth2ClientHttpRequestInterceptor interceptor(String registration) {
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

  private static java.util.function.Consumer<List<ClientHttpRequestInterceptor>> add(
      OAuth2ClientHttpRequestInterceptor tokens) {
    return interceptors -> {
      interceptors.add(new RetryOnceOnUnauthorized());
      interceptors.add(tokens);
    };
  }

  static class RetryOnceOnUnauthorized implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      var response = execution.execute(request, body);
      if (!response.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) return response;
      var challenge = response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
      if (challenge != null && !challenge.contains(SecurityConstants.INVALID_TOKEN))
        return response;
      response.close();
      return execution.execute(request, body);
    }
  }
}

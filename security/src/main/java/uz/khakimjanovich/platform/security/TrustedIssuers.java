package uz.khakimjanovich.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.web.client.RestTemplate;

final class TrustedIssuers {

  private TrustedIssuers() {}

  static AuthenticationManagerResolver<HttpServletRequest> resolver(
      List<SecurityProperties.Issuer> issuers,
      KeycloakJwtConverter converter,
      @Nullable SslBundle bundle) {
    // SecurityProperties checks each issuer is listed once.
    Map<String, AuthenticationManager> managers = new HashMap<>();
    for (var issuer : issuers) {
      var granted =
          issuer.roles().stream().map(KeycloakJwtConverter::authority).collect(Collectors.toSet());
      var provider =
          new JwtAuthenticationProvider(decoder(issuer.uri(), issuer.audience(), bundle));
      provider.setJwtAuthenticationConverter(
          jwt -> {
            var token = converter.convert(jwt);
            // Only the roles this issuer may grant count; any other in the token is dropped.
            var roles =
                token.getAuthorities().stream()
                    .filter(authority -> granted.contains(authority.getAuthority()))
                    .toList();
            return new JwtAuthenticationToken(jwt, roles, token.getName());
          });
      managers.put(issuer.uri(), new ProviderManager(provider));
    }
    return new JwtIssuerAuthenticationManagerResolver(
        uri -> {
          var manager = managers.get(uri);
          if (manager == null) {
            throw new InvalidBearerTokenException(SecurityConstants.ISSUER_NOT_TRUSTED);
          }
          return manager;
        });
  }

  static JwtDecoder decoder(String uri, String audience, @Nullable SslBundle bundle) {
    return new SupplierJwtDecoder(
        () -> {
          var location = NimbusJwtDecoder.withIssuerLocation(uri);
          // Its discovery document and keys, over the bundle's trust (JDK client: host names are
          // checked).
          if (bundle != null) {
            location.restOperations(
                new RestTemplate(
                    new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().sslContext(bundle.createSslContext()).build())));
          }
          var decoder = location.build();
          decoder.setJwtValidator(
              new DelegatingOAuth2TokenValidator<>(
                  JwtValidators.createDefaultWithIssuer(uri),
                  new JwtClaimValidator<List<String>>(
                      JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience))));
          return decoder;
        });
  }
}

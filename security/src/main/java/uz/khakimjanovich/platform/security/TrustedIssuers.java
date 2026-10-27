package uz.khakimjanovich.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

/**
 * Tokens from several issuers, each with the clients it may send: the token's issuer picks its
 * keys, and a token from any other issuer, for another audience, or from a client not listed is
 * refused (401). Each issuer's keys are fetched with its first token, not at startup.
 */
final class TrustedIssuers {

  private TrustedIssuers() {}

  static AuthenticationManagerResolver<HttpServletRequest> resolver(
      List<SecurityProperties.Issuer> issuers, KeycloakJwtConverter converter) {
    Map<String, AuthenticationManager> managers = new HashMap<>();
    for (var issuer : issuers) {
      var provider = new JwtAuthenticationProvider(decoder(issuer));
      provider.setJwtAuthenticationConverter(converter);
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

  static JwtDecoder decoder(SecurityProperties.Issuer issuer) {
    return new SupplierJwtDecoder(
        () -> {
          var decoder = NimbusJwtDecoder.withIssuerLocation(issuer.uri()).build();
          decoder.setJwtValidator(
              new DelegatingOAuth2TokenValidator<>(
                  JwtValidators.createDefaultWithIssuer(issuer.uri()),
                  new JwtClaimValidator<List<String>>(
                      JwtClaimNames.AUD,
                      audience -> audience != null && audience.contains(issuer.audience())),
                  new JwtClaimValidator<String>(
                      SecurityConstants.CLIENT_CLAIM,
                      client -> client != null && issuer.clients().contains(client))));
          return decoder;
        });
  }
}

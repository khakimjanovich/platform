package uz.heeypay.ecom.security;

import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Who may call what. {@code public} routes need no token; {@code roles} maps a route to the realm
 * role it needs, checked in the order written. Any route not listed is refused. A route may start
 * with an HTTP method: {@code "POST /api/v1/registrations/**"}.
 */
@ConfigurationProperties(SecurityConstantUtils.PROPERTIES_PREFIX)
public record SecurityProperties(
    @DefaultValue List<String> publicRoutes, @DefaultValue LinkedHashMap<String, String> roles) {}

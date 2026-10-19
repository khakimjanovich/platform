package uz.heeypay.ecom.otp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Every limit is configuration, not code. The secret keys the stored hashes and has no default. */
@ConfigurationProperties(OtpConstants.PROPERTIES_PREFIX)
public record OtpProperties(
    String secret,
    @DefaultValue(OtpConstants.DEFAULT_LENGTH) int length,
    @DefaultValue(OtpConstants.DEFAULT_TTL) Duration ttl,
    @DefaultValue(OtpConstants.DEFAULT_MAX_ATTEMPTS) int maxAttempts,
    @DefaultValue(OtpConstants.DEFAULT_MAX_SENDS) int maxSends,
    @DefaultValue(OtpConstants.DEFAULT_SEND_WINDOW) Duration sendWindow) {}

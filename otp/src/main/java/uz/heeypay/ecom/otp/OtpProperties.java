package uz.heeypay.ecom.otp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Every limit is configuration, not code. */
@ConfigurationProperties(OtpConstantUtils.PROPERTIES_PREFIX)
public record OtpProperties(
    @DefaultValue(OtpConstantUtils.DEFAULT_LENGTH) int length,
    @DefaultValue(OtpConstantUtils.DEFAULT_TTL) Duration ttl,
    @DefaultValue(OtpConstantUtils.DEFAULT_MAX_ATTEMPTS) int maxAttempts,
    @DefaultValue(OtpConstantUtils.DEFAULT_MAX_SENDS) int maxSends,
    @DefaultValue(OtpConstantUtils.DEFAULT_SEND_WINDOW) Duration sendWindow) {}

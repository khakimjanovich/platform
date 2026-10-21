package uz.heeypay.ecom.otp;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Every limit is configuration, not code. The secret keys the stored hashes and has no default.
 *
 * @param secret key of the HMAC-SHA256 hashes stored for each code and subject; required
 * @param length digits in a code, 4 to 10
 * @param ttl how long a code stays valid
 * @param maxAttempts wrong codes allowed before the code is locked
 * @param maxSends codes sent to one subject in one send window
 * @param sendWindow the window {@code max-sends} counts in
 */
@Validated
@ConfigurationProperties(OtpConstants.PROPERTIES_PREFIX)
public record OtpProperties(
    String secret,
    @DefaultValue(OtpConstants.DEFAULT_LENGTH) @Min(4) @Max(10) int length,
    @DefaultValue(OtpConstants.DEFAULT_TTL) @DurationMin(nanos = 1) Duration ttl,
    @DefaultValue(OtpConstants.DEFAULT_MAX_ATTEMPTS) @Positive int maxAttempts,
    @DefaultValue(OtpConstants.DEFAULT_MAX_SENDS) @Positive int maxSends,
    @DefaultValue(OtpConstants.DEFAULT_SEND_WINDOW) @DurationMin(nanos = 1) Duration sendWindow) {}

package uz.khakimjanovich.platform.otp;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Every limit is the service's policy, so each one is required: none has a default, and a missing
 * or unworkable one stops startup, naming the property. The secret keys the stored hashes.
 *
 * @param secret key of the HMAC-SHA256 hashes stored for each code and subject; required
 * @param length digits in a code, 4 to 10
 * @param ttl how long a code stays valid
 * @param maxAttempts wrong codes allowed before the code is locked
 * @param maxSends codes sent to one subject in one send window
 * @param sendWindow the window {@code max-sends} and wrong codes count in; a lockout lasts until it
 *     ends
 * @param resendAfter how long after a code the next one may be sent; 0 for no wait
 * @param keyPrefix the first part of every Redis key the library writes
 */
@Validated
@ConfigurationProperties(OtpConstants.PROPERTIES_PREFIX)
public record OtpProperties(
    String secret,
    @NotNull @Min(4) @Max(10) Integer length,
    @NotNull @DurationMin(nanos = 1) Duration ttl,
    @NotNull @Positive Integer maxAttempts,
    @NotNull @Positive Integer maxSends,
    @NotNull @DurationMin(nanos = 1) Duration sendWindow,
    @NotNull @DurationMin(nanos = 0) Duration resendAfter,
    @NotBlank String keyPrefix) {}

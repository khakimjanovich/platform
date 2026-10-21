package uz.heeypay.ecom.filestore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * The S3-compatible store, and the buckets the app may use: which file types each accepts and how
 * big a file may be.
 *
 * @param storage the S3-compatible store
 * @param buckets the buckets the app may use, by name, with their rules
 */
@Validated
@ConfigurationProperties(FileStoreConstants.PROPERTIES_PREFIX)
public record FileStoreProperties(
    @Valid @NotNull Storage storage, @NotEmpty Map<String, @Valid Bucket> buckets) {

  /**
   * Where the store is and how to sign in.
   *
   * @param endpoint the store's URL, e.g. {@code http://localhost:8333}; setting it turns the
   *     library on
   * @param region the region requests are signed for
   * @param credentials {@code keys} (the access and secret key, both required) or {@code
   *     default-chain} (AWS's default credentials chain: environment, profile, container or
   *     instance role), chosen on purpose so a missing secret never means "use the instance role"
   * @param accessKey the app's access key
   * @param secretKey the app's secret key
   * @param pathStyle bucket in the path ({@code host/bucket/key}) instead of the host name
   * @param apiCallTimeout how long one store call may take, retries included
   * @param apiCallAttemptTimeout how long one attempt of a call may take
   */
  public record Storage(
      @NotBlank String endpoint,
      @NotBlank String region,
      @DefaultValue(FileStoreConstants.DEFAULT_CREDENTIALS) Credentials credentials,
      String accessKey,
      String secretKey,
      boolean pathStyle,
      @DefaultValue(FileStoreConstants.DEFAULT_API_CALL_TIMEOUT) Duration apiCallTimeout,
      @DefaultValue(FileStoreConstants.DEFAULT_API_CALL_ATTEMPT_TIMEOUT)
          Duration apiCallAttemptTimeout) {

    /** How the app signs in to the store. */
    public enum Credentials {
      KEYS,
      DEFAULT_CHAIN
    }

    @AssertTrue(
        message = "access-key and secret-key are required unless credentials is default-chain")
    public boolean isSecretKeySet() {
      return credentials == Credentials.DEFAULT_CHAIN
          || (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey));
    }
  }

  /**
   * What one bucket accepts.
   *
   * @param allowedTypes media types, read from the bytes, the bucket accepts
   * @param maxSize the largest file the bucket accepts
   */
  public record Bucket(@NotEmpty Set<String> allowedTypes, @NotNull DataSize maxSize) {

    @AssertTrue(message = "max-size must be greater than 0")
    public boolean isMaxSizePositive() {
      return maxSize == null || maxSize.toBytes() > 0;
    }
  }
}

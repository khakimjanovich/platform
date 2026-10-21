package uz.heeypay.ecom.filestore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
public record FileStoreProperties(Storage storage, Map<String, @Valid Bucket> buckets) {

  /**
   * Where the store is and how to sign in.
   *
   * @param endpoint the store's URL, e.g. {@code http://localhost:8333}; setting it turns the
   *     library on
   * @param region the region requests are signed for
   * @param accessKey the app's access key
   * @param secretKey the app's secret key
   * @param pathStyle bucket in the path ({@code host/bucket/key}) instead of the host name
   */
  public record Storage(
      String endpoint, String region, String accessKey, String secretKey, boolean pathStyle) {}

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

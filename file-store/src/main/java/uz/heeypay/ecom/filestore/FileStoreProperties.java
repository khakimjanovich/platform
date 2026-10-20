package uz.heeypay.ecom.filestore;

import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * The S3-compatible store, and the buckets the app may use: which file types each accepts and how
 * big a file may be.
 */
@ConfigurationProperties(FileStoreConstants.PROPERTIES_PREFIX)
public record FileStoreProperties(Storage storage, Map<String, Bucket> buckets) {

  public record Storage(
      String endpoint, String region, String accessKey, String secretKey, boolean pathStyle) {}

  public record Bucket(Set<String> allowedTypes, DataSize maxSize) {}
}

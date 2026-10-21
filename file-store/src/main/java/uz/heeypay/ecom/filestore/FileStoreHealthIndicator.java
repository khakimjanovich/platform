package uz.heeypay.ecom.filestore;

import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * UP when every configured bucket answers a HeadBucket with the app's credentials. Otherwise DOWN,
 * naming each failing bucket with the store's error code, or the HTTP status when a HEAD answer has
 * none (403 for a rejected signature, 404 for a missing bucket), never a key.
 */
@RequiredArgsConstructor
class FileStoreHealthIndicator implements HealthIndicator {

  private final S3Client s3;
  private final FileStoreProperties properties;

  @Override
  public Health health() {
    var failed = new LinkedHashMap<String, String>();
    for (var name : properties.buckets().keySet()) {
      try {
        s3.headBucket(
            head ->
                head.bucket(name)
                    .overrideConfiguration(
                        timeout -> timeout.apiCallTimeout(FileStoreConstants.HEALTH_CALL_TIMEOUT)));
      } catch (AwsServiceException e) {
        var code = e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode();
        failed.put(name, code != null ? code : String.valueOf(e.statusCode()));
      } catch (SdkException e) {
        failed.put(name, e.getClass().getSimpleName());
      }
    }
    return failed.isEmpty() ? Health.up().build() : Health.down().withDetails(failed).build();
  }
}

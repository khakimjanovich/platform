package uz.heeypay.ecom.filestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;

class FileStoreHealthIndicatorTest {

  private final S3Client s3 = mock(S3Client.class);
  private final FileStoreHealthIndicator indicator =
      new FileStoreHealthIndicator(
          s3,
          new FileStoreProperties(
              null,
              Map.of(
                  "partner-documents",
                  new FileStoreProperties.Bucket(
                      Set.of("application/pdf"), DataSize.ofMegabytes(1)))));

  @Test
  @SuppressWarnings("unchecked")
  void upWhenEveryBucketAnswers() {
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  @SuppressWarnings("unchecked")
  void downWithTheStoresErrorCode() {
    when(s3.headBucket(any(Consumer.class)))
        .thenThrow(
            AwsServiceException.builder()
                .statusCode(403)
                .awsErrorDetails(
                    AwsErrorDetails.builder().errorCode("SignatureDoesNotMatch").build())
                .build());
    var health = indicator.health();
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("partner-documents", "SignatureDoesNotMatch");
  }

  @Test
  @SuppressWarnings("unchecked")
  void downWhenTheStoreDoesNotAnswer() {
    when(s3.headBucket(any(Consumer.class))).thenThrow(SdkClientException.create("refused"));
    var health = indicator.health();
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("partner-documents", "SdkClientException");
  }
}

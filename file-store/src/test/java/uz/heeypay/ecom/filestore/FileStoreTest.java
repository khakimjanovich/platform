package uz.heeypay.ecom.filestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import uz.heeypay.ecom.error.GeneralException;

/** The store's own rules, with the S3 client mocked. */
@ExtendWith(OutputCaptureExtension.class)
class FileStoreTest {

  private static final String PDF_SHA256 =
      "d8b9a8d1b9d8b7b6a5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b7c6d5e4f3a2";

  private final S3Client s3 = mock(S3Client.class);
  private final FileStore files =
      new FileStore(
          s3,
          new FileStoreProperties(
              null,
              Map.of(
                  "partner-documents",
                  new FileStoreProperties.Bucket(
                      Set.of("application/pdf"), DataSize.ofMegabytes(10)))));

  @Test
  @SuppressWarnings("unchecked")
  void aMissingBucketStopsStartupNamingIt() {
    when(s3.headBucket(any(Consumer.class)))
        .thenThrow(NoSuchBucketException.builder().statusCode(404).message("no").build());
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FileStoreAutoConfiguration.class))
        .withBean(S3Client.class, () -> s3)
        .withPropertyValues(
            "ecom.file-store.storage.endpoint=http://localhost:8333",
            "ecom.file-store.storage.access-key=ecom",
            "ecom.file-store.storage.secret-key=local-only",
            "ecom.file-store.buckets.partner-documents.allowed-types=application/pdf",
            "ecom.file-store.buckets.partner-documents.max-size=10MB")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(NoSuchBucketException.class)
                    .satisfies(
                        cause ->
                            assertThat(context.getStartupFailure())
                                .hasStackTraceContaining("Bucket partner-documents")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void aFileThatNoLongerMatchesItsChecksumIsCorrupt() {
    var bytes = "%PDF-1.4 changed".getBytes(StandardCharsets.US_ASCII);
    var response =
        GetObjectResponse.builder()
            .contentType("application/pdf")
            .contentLength((long) bytes.length)
            .metadata(
                Map.of(
                    "file-name", "Ustav.pdf",
                    "sha256", PDF_SHA256,
                    "uploaded-at", "2026-10-21T00:00:00Z"))
            .build();
    when(s3.getObjectAsBytes(any(Consumer.class)))
        .thenReturn(ResponseBytes.fromByteArray(response, bytes));
    assertThatThrownBy(() -> files.read("partner-documents", UUID.randomUUID()))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus())
                    .isEqualTo(FileStoreExceptionStatus.FILE_STORE_FILE_CORRUPT));
  }

  /** An object the store didn't write (no name, no checksum) is not one of its files. */
  @Test
  @SuppressWarnings("unchecked")
  void anObjectWithoutTheStoresMetadataIsCorrupt() {
    var response = GetObjectResponse.builder().contentType("application/pdf").build();
    when(s3.getObjectAsBytes(any(Consumer.class)))
        .thenReturn(ResponseBytes.fromByteArray(response, new byte[] {1, 2, 3}));
    assertThatThrownBy(() -> files.read("partner-documents", UUID.randomUUID()))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus())
                    .isEqualTo(FileStoreExceptionStatus.FILE_STORE_FILE_CORRUPT));
  }

  @Test
  @SuppressWarnings("unchecked")
  void aStoreThatDoesntAnswerIsUnavailable(CapturedOutput output) {
    when(s3.getObjectAsBytes(any(Consumer.class)))
        .thenThrow(SdkClientException.create("connection refused"));
    assertThatThrownBy(() -> files.read("partner-documents", UUID.randomUUID()))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus())
                    .isEqualTo(FileStoreExceptionStatus.FILE_STORE_STORAGE_UNAVAILABLE));
    assertThat(output).contains("File store unavailable: connection refused");
  }

  @Test
  void anUnknownBucketIsTheCodesMistake() {
    assertThatThrownBy(() -> files.read("anything", UUID.randomUUID()))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus())
                    .isEqualTo(FileStoreExceptionStatus.FILE_STORE_BUCKET_UNKNOWN));
  }
}

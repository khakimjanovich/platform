package uz.heeypay.ecom.filestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import uz.heeypay.ecom.error.GeneralException;

/** The file store, storing into a real SeaweedFS. */
@SpringBootTest(
    classes = FileStoreIT.class,
    properties = {
      "ecom.file-store.storage.region=us-east-1",
      "ecom.file-store.storage.access-key=ecom",
      "ecom.file-store.storage.secret-key=local-only",
      "ecom.file-store.storage.path-style=true",
      "ecom.file-store.buckets.partner-documents.allowed-types=application/pdf,image/png,image/jpeg",
      "ecom.file-store.buckets.partner-documents.max-size=10MB",
      "ecom.file-store.buckets.sales-point-logos.allowed-types=image/png,image/jpeg",
      "ecom.file-store.buckets.sales-point-logos.max-size=1MB"
    })
@ImportAutoConfiguration(FileStoreAutoConfiguration.class)
@Testcontainers
class FileStoreIT {

  @Container
  static final GenericContainer<?> SEAWEEDFS =
      new GenericContainer<>("chrislusf/seaweedfs:3.97")
          .withCommand(
              "server",
              "-s3",
              "-s3.config=/etc/seaweedfs/s3.json",
              "-dir=/data",
              "-master.volumeSizeLimitMB=64",
              "-volume.max=8")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("seaweedfs-s3.json"), "/etc/seaweedfs/s3.json")
          .withExposedPorts(8333)
          .waitingFor(
              Wait.forHttp("/")
                  .forPort(8333)
                  .forStatusCode(403)
                  .withStartupTimeout(Duration.ofMinutes(2)));

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) {
    registry.add(
        "ecom.file-store.storage.endpoint",
        () -> "http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(8333));
  }

  @Autowired FileStore files;

  @Test
  void aFileIsStoredWithItsTypeAndChecksumAndReadBackAsIs() {
    var pdf =
        "%PDF-1.4\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);
    var stored = files.store("partner-documents", file("../../Ustav.png", "image/png", pdf));
    assertThat(stored.contentType())
        .as("from the bytes, not the name")
        .isEqualTo("application/pdf");
    assertThat(stored.fileName()).isEqualTo("Ustav.png");
    assertThat(stored.sha256()).hasSize(64);

    var read = files.read("partner-documents", stored.id());
    assertThat(read.bytes()).isEqualTo(pdf);
    assertThat(read.file()).isEqualTo(stored);

    files.delete("partner-documents", stored.id());
    assertThatThrownBy(() -> files.read("partner-documents", stored.id()))
        .satisfies(e -> hasCode(e, FileStoreExceptionStatus.FILE_STORE_FILE_NOT_FOUND));
  }

  @Test
  void eachBucketTakesOnlyItsTypesAndSizes() throws Exception {
    var svg = "<svg onload=alert(1)/>".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> files.store("sales-point-logos", file("logo.png", "image/png", svg)))
        .satisfies(e -> hasCode(e, FileStoreExceptionStatus.FILE_STORE_TYPE_NOT_ALLOWED));
    var big = new byte[1_100_000];
    assertThatThrownBy(() -> files.store("sales-point-logos", file("logo.png", "image/png", big)))
        .satisfies(e -> hasCode(e, FileStoreExceptionStatus.FILE_STORE_FILE_TOO_LARGE));
    assertThat(files.store("sales-point-logos", file("logo.png", "image/png", png())).contentType())
        .isEqualTo("image/png");
    assertThatThrownBy(() -> files.store("anything", file("a.png", "image/png", png())))
        .satisfies(e -> hasCode(e, FileStoreExceptionStatus.FILE_STORE_BUCKET_UNKNOWN));
  }

  private static void hasCode(Throwable e, FileStoreExceptionStatus status) {
    assertThat(e).isInstanceOf(GeneralException.class);
    assertThat(((GeneralException) e).getStatus()).isEqualTo(status);
  }

  private static MockMultipartFile file(String name, String type, byte[] bytes) {
    return new MockMultipartFile("file", name, type, bytes);
  }

  private static byte[] png() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }
}

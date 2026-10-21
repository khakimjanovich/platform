package uz.heeypay.ecom.filestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ResourceBundle;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import uz.heeypay.ecom.error.GeneralException;

/**
 * The file store, storing into a real SeaweedFS as an identity that may only read, write and list
 * its two buckets; the buckets are created beforehand, as ecom-infra does.
 */
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
      new GenericContainer<>(ResourceBundle.getBundle("images").getString("seaweedfs"))
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

  /** As ecom-infra's seaweedfs/init.sh: the buckets come from infrastructure, not from the app. */
  @BeforeAll
  static void buckets() throws Exception {
    for (var bucket : new String[] {"partner-documents", "sales-point-logos"}) {
      var created =
          SEAWEEDFS.execInContainer(
              "sh", "-c", "echo 's3.bucket.create -name " + bucket + "' | weed shell");
      assertThat(created.getExitCode()).as(created.getStderr()).isZero();
    }
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) {
    registry.add("ecom.file-store.storage.endpoint", FileStoreIT::endpoint);
  }

  private static String endpoint() {
    return "http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(8333);
  }

  @Autowired FileStore files;
  @Autowired S3Client s3;

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

  @Test
  void aConfiguredBucketTheStoreLacksStopsStartup() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FileStoreAutoConfiguration.class))
        .withPropertyValues(
            "ecom.file-store.storage.endpoint=" + endpoint(),
            "ecom.file-store.storage.region=us-east-1",
            "ecom.file-store.storage.access-key=ecom",
            "ecom.file-store.storage.secret-key=local-only",
            "ecom.file-store.storage.path-style=true",
            "ecom.file-store.buckets.contracts.allowed-types=application/pdf",
            "ecom.file-store.buckets.contracts.max-size=1MB")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .isNotNull()
                    .hasStackTraceContaining("Bucket contracts"));
  }

  @Test
  void aFileChangedBehindTheStoresBackIsCorrupt() {
    var pdf = "%PDF-1.4\ntrailer << >>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    var stored = files.store("partner-documents", file("Ustav.pdf", "application/pdf", pdf));
    var original =
        s3.headObject(head -> head.bucket("partner-documents").key(stored.id().toString()));
    s3.putObject(
        put ->
            put.bucket("partner-documents")
                .key(stored.id().toString())
                .contentType("application/pdf")
                .metadata(original.metadata()),
        RequestBody.fromBytes("%PDF-1.4 tampered %%EOF".getBytes(StandardCharsets.US_ASCII)));
    assertThatThrownBy(() -> files.read("partner-documents", stored.id()))
        .satisfies(e -> hasCode(e, FileStoreExceptionStatus.FILE_STORE_FILE_CORRUPT));
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

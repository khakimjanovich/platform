package uz.khakimjanovich.platform.filestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

/** A bucket whose rules can't work, or a missing secret, stops startup, naming the property. */
class FileStorePropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(FileStoreAutoConfiguration.class))
          .withBean(S3Client.class, () -> mock(S3Client.class)) // no store needed to check rules
          .withPropertyValues(
              "platform.file-store.storage.endpoint=http://localhost:8333",
              "platform.file-store.storage.region=us-east-1",
              "platform.file-store.storage.access-key=demo",
              "platform.file-store.buckets.logos.allowed-types=image/png",
              "platform.file-store.buckets.logos.max-size=1MB");

  @Test
  void aBucketWithAPositiveLimitStarts() {
    runner
        .withPropertyValues("platform.file-store.storage.secret-key=local-only")
        .run(context -> assertThat(context).hasSingleBean(FileStore.class));
  }

  @Test
  void aBucketThatTakesNothingStopsStartup() {
    runner
        .withPropertyValues(
            "platform.file-store.storage.secret-key=local-only",
            "platform.file-store.buckets.logos.max-size=0B")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("platform.file-store")
                    .hasMessageContaining("max-size"));
  }

  @Test
  void aMissingSecretKeyStopsStartup() {
    runner.run(
        context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("platform.file-store")
                .hasMessageContaining("secret-key"));
  }

  @Test
  void theDefaultChainIsChosenOnPurpose() {
    runner
        .withPropertyValues("platform.file-store.storage.credentials=default-chain")
        .run(context -> assertThat(context).hasSingleBean(FileStore.class));
  }

  @Test
  void aStoreWithoutARegionStopsStartup() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FileStoreAutoConfiguration.class))
        .withBean(S3Client.class, () -> mock(S3Client.class))
        .withPropertyValues(
            "platform.file-store.storage.endpoint=http://localhost:8333",
            "platform.file-store.storage.access-key=demo",
            "platform.file-store.storage.secret-key=local-only",
            "platform.file-store.buckets.logos.allowed-types=image/png",
            "platform.file-store.buckets.logos.max-size=1MB")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("platform.file-store")
                    .hasMessageContaining("region"));
  }
}

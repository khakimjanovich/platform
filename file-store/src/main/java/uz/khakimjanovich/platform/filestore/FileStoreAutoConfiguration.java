package uz.khakimjanovich.platform.filestore;

import java.net.URI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@AutoConfiguration
@ConditionalOnProperty(prefix = FileStoreConstants.PROPERTIES_PREFIX, name = "storage.endpoint")
@EnableConfigurationProperties(FileStoreProperties.class)
public class FileStoreAutoConfiguration {

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  S3Client s3Client(FileStoreProperties properties) {
    var storage = properties.storage();
    return S3Client.builder()
        .endpointOverride(URI.create(storage.endpoint()))
        .region(Region.of(storage.region()))
        .credentialsProvider(credentials(storage))
        .overrideConfiguration(
            timeouts ->
                timeouts
                    .apiCallTimeout(storage.apiCallTimeout())
                    .apiCallAttemptTimeout(storage.apiCallAttemptTimeout()))
        // S3-compatible stores (SeaweedFS, MinIO) read plain uploads best: no aws-chunked framing,
        // checksums only where the API requires them.
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(storage.pathStyle())
                .chunkedEncodingEnabled(false)
                .build())
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  FileStore fileStore(S3Client s3, FileStoreProperties properties) {
    var store = new FileStore(s3, properties);
    store.checkBuckets();
    return store;
  }

  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(name = FileStoreConstants.HEALTH_INDICATOR_BEAN)
  HealthIndicator fileStoreHealthIndicator(S3Client s3, FileStoreProperties properties) {
    return new FileStoreHealthIndicator(s3, properties);
  }

  private static AwsCredentialsProvider credentials(FileStoreProperties.Storage storage) {
    if (storage.credentials() == FileStoreProperties.Storage.Credentials.DEFAULT_CHAIN) {
      return DefaultCredentialsProvider.builder().build();
    }
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
  }
}

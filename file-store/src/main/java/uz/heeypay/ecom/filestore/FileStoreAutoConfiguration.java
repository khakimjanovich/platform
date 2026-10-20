package uz.heeypay.ecom.filestore;

import java.net.URI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Setting {@code ecom.file-store.storage.endpoint} gives the app a {@link FileStore}: any
 * S3-compatible store, SeaweedFS locally (path-style addresses), AWS S3 or MinIO elsewhere.
 */
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
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())))
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
    return new FileStore(s3, properties);
  }
}

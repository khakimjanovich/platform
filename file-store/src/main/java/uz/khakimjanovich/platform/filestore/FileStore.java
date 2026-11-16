package uz.khakimjanovich.platform.filestore;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uz.khakimjanovich.platform.error.GeneralException;
import uz.khakimjanovich.platform.filestore.FileStoreProperties.Bucket;

@Slf4j
@RequiredArgsConstructor
public class FileStore {

  private final S3Client s3;
  private final FileStoreProperties properties;

  public record StoredFile(
      UUID id,
      String bucket,
      String fileName,
      String contentType,
      long size,
      String sha256,
      Instant uploadedAt) {}

  public static final class FileContent {
    private final StoredFile file;
    private final byte[] bytes;

    public FileContent(StoredFile file, byte[] bytes) {
      this.file = file;
      this.bytes = bytes;
    }

    public StoredFile file() {
      return file;
    }

    public byte[] bytes() {
      return bytes;
    }
  }

  public record StreamingFileContent(StoredFile file, InputStream stream) implements AutoCloseable {

    public void transferTo(OutputStream target) throws IOException {
      stream.transferTo(target);
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }
  }

  public void checkBuckets() {
    for (var name : properties.buckets().keySet()) {
      try {
        s3.headBucket(head -> head.bucket(name));
      } catch (NoSuchBucketException e) {
        throw new IllegalStateException(FileStoreConstants.BUCKET_MISSING.formatted(name), e);
      } catch (S3Exception e) {
        if (e.statusCode() == 404) {
          throw new IllegalStateException(FileStoreConstants.BUCKET_MISSING.formatted(name), e);
        }
        throw new IllegalStateException(FileStoreConstants.BUCKET_UNREADABLE.formatted(name), e);
      } catch (SdkException e) {
        // The store doesn't answer (refused, a timeout): say which bucket and why, not a raw SDK
        // trace.
        throw new IllegalStateException(FileStoreConstants.BUCKET_UNCHECKED.formatted(name), e);
      }
    }
  }

  public StoredFile store(String bucket, MultipartFile file) {
    var rules = bucket(bucket);
    if (file.isEmpty()) throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_FILE_EMPTY);
    if (file.getSize() > rules.maxSize().toBytes()) {
      throw new GeneralException(
          FileStoreExceptionStatus.FILE_STORE_FILE_TOO_LARGE, rules.maxSize());
    }
    var bytes = bytes(file);
    var type =
        FileTypeUtils.detect(bytes)
            .filter(rules.allowedTypes()::contains)
            .orElseThrow(
                () ->
                    new GeneralException(
                        FileStoreExceptionStatus.FILE_STORE_TYPE_NOT_ALLOWED,
                        String.join(", ", new TreeSet<>(rules.allowedTypes()))));
    var stored =
        new StoredFile(
            UUID.randomUUID(),
            bucket,
            FileTypeUtils.safeName(file.getOriginalFilename()),
            type,
            bytes.length,
            sha256(bytes),
            Instant.now());
    storage(
        () ->
            s3.putObject(
                put ->
                    put.bucket(bucket)
                        .key(stored.id().toString())
                        .contentType(type)
                        .metadata(
                            Map.of(
                                FileStoreConstants.FILE_NAME_METADATA, stored.fileName(),
                                FileStoreConstants.SHA256_METADATA, stored.sha256(),
                                FileStoreConstants.UPLOADED_AT_METADATA,
                                    stored.uploadedAt().toString())),
                RequestBody.fromBytes(bytes)));
    log.info(FileStoreConstants.LOG_STORED, stored.id(), bucket, bytes.length, type);
    return stored;
  }

  public StreamingFileContent openStream(String bucket, UUID fileId) {
    bucket(bucket);
    var stream =
        storage(() -> s3.getObject(get -> get.bucket(bucket).key(fileId.toString())), fileId);
    var response = stream.response();
    var metadata = response.metadata();
    var name = metadata.get(FileStoreConstants.FILE_NAME_METADATA);
    var checksum = metadata.get(FileStoreConstants.SHA256_METADATA);
    var uploadedAt = metadata.get(FileStoreConstants.UPLOADED_AT_METADATA);
    if (name == null || uploadedAt == null || checksum == null) {
      try {
        stream.close();
      } catch (IOException e) {
        log.debug("Stream close failed for corrupt file {} in {}", fileId, bucket, e);
      }
      log.error(FileStoreConstants.LOG_CORRUPT, fileId, bucket);
      throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_FILE_CORRUPT, fileId);
    }
    var storedFile =
        new StoredFile(
            fileId,
            bucket,
            name,
            response.contentType(),
            response.contentLength(),
            checksum,
            Instant.parse(uploadedAt));
    return new StreamingFileContent(
        storedFile, new VerifyingInputStream(stream, checksum, fileId, bucket));
  }

  public void transferTo(String bucket, UUID fileId, OutputStream target) throws IOException {
    try (var streaming = openStream(bucket, fileId)) {
      streaming.transferTo(target);
    }
  }

  public FileContent read(String bucket, UUID fileId) {
    try (var streaming = openStream(bucket, fileId)) {
      return new FileContent(streaming.file(), streaming.stream().readAllBytes());
    } catch (IOException e) {
      log.warn(FileStoreConstants.LOG_UNAVAILABLE, e.getMessage(), e);
      throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_STORAGE_UNAVAILABLE);
    }
  }

  public void delete(String bucket, UUID fileId) {
    bucket(bucket);
    try {
      s3.deleteObject(d -> d.bucket(bucket).key(fileId.toString()));
    } catch (SdkException e) {
      log.warn("File {} in {} was not deleted: {}", fileId, bucket, e.getMessage());
    }
  }

  private Bucket bucket(String name) {
    var bucket = properties.buckets().get(name);
    if (bucket == null) {
      throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_BUCKET_UNKNOWN, name);
    }
    return bucket;
  }

  private static <T> T storage(Supplier<T> call) {
    try {
      return call.get();
    } catch (SdkException e) {
      throw unavailable(e);
    }
  }

  private static <T> T storage(Supplier<T> call, UUID fileId) {
    try {
      return call.get();
    } catch (NoSuchKeyException e) {
      throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_FILE_NOT_FOUND, fileId);
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_FILE_NOT_FOUND, fileId);
      }
      throw unavailable(e);
    } catch (SdkException e) {
      throw unavailable(e);
    }
  }

  private static GeneralException unavailable(SdkException e) {
    log.warn(FileStoreConstants.LOG_UNAVAILABLE, e.getMessage(), e);
    return new GeneralException(FileStoreExceptionStatus.FILE_STORE_STORAGE_UNAVAILABLE);
  }

  private static byte[] bytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_FILE_EMPTY);
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class VerifyingInputStream extends FilterInputStream {
    private final MessageDigest digest;
    private final String expectedChecksum;
    private final UUID fileId;
    private final String bucket;
    private boolean verified;
    private boolean eof;

    private VerifyingInputStream(
        InputStream in, String expectedChecksum, UUID fileId, String bucket) {
      super(in);
      this.expectedChecksum = expectedChecksum;
      this.fileId = fileId;
      this.bucket = bucket;
      try {
        this.digest = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public int read() throws IOException {
      int b = super.read();
      if (b != -1) {
        digest.update((byte) b);
      } else {
        eof = true;
        verifyChecksum();
      }
      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int count = super.read(b, off, len);
      if (count != -1) {
        digest.update(b, off, count);
      } else {
        eof = true;
        verifyChecksum();
      }
      return count;
    }

    @Override
    public void close() throws IOException {
      try {
        super.close();
      } finally {
        if (eof) {
          verifyChecksum();
        }
      }
    }

    private void verifyChecksum() {
      if (!verified) {
        verified = true;
        String computed = HexFormat.of().formatHex(digest.digest());
        if (!computed.equals(expectedChecksum)) {
          log.error(FileStoreConstants.LOG_CORRUPT, fileId, bucket);
          throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_FILE_CORRUPT, fileId);
        }
      }
    }
  }
}

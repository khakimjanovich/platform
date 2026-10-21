package uz.heeypay.ecom.filestore;

import java.io.IOException;
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
import uz.heeypay.ecom.error.GeneralException;
import uz.heeypay.ecom.filestore.FileStoreProperties.Bucket;

/**
 * Files as S3 objects: the key is a random id, and what the file is (name, type, checksum, upload
 * time) travels with the object as its metadata, so no database is needed. The type is read from
 * the file's own bytes, never from what the uploader claims; a file read back must still match its
 * checksum. The buckets are infrastructure's: the store only checks at startup that each configured
 * one exists, so the app needs no admin rights.
 */
@Slf4j
@RequiredArgsConstructor
public class FileStore {

  private final S3Client s3;
  private final FileStoreProperties properties;

  /** What a stored file is. */
  public record StoredFile(
      UUID id,
      String bucket,
      String fileName,
      String contentType,
      long size,
      String sha256,
      Instant uploadedAt) {}

  /** A stored file and its bytes. */
  public record FileContent(StoredFile file, byte[] bytes) {}

  /** At startup: every configured bucket must exist; creating them is infrastructure's job. */
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
      }
    }
  }

  /** Checks the file against the bucket's rules (not empty, small enough, an allowed type). */
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

  public FileContent read(String bucket, UUID fileId) {
    bucket(bucket);
    var object =
        storage(
            () -> s3.getObjectAsBytes(get -> get.bucket(bucket).key(fileId.toString())), fileId);
    var response = object.response();
    var metadata = response.metadata();
    var bytes = object.asByteArray();
    if (!sha256(bytes).equals(metadata.get(FileStoreConstants.SHA256_METADATA))) {
      log.error(FileStoreConstants.LOG_CORRUPT, fileId, bucket);
      throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_FILE_CORRUPT, fileId);
    }
    return new FileContent(
        new StoredFile(
            fileId,
            bucket,
            metadata.get(FileStoreConstants.FILE_NAME_METADATA),
            response.contentType(),
            response.contentLength(),
            metadata.get(FileStoreConstants.SHA256_METADATA),
            Instant.parse(metadata.get(FileStoreConstants.UPLOADED_AT_METADATA))),
        bytes);
  }

  /** Best effort: a file nobody points to any more is only wasted space. */
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
      throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_STORAGE_UNAVAILABLE);
    }
  }

  /** A missing object is the caller's 404; anything else, the store's trouble. */
  private static <T> T storage(Supplier<T> call, UUID fileId) {
    try {
      return call.get();
    } catch (NoSuchKeyException e) {
      throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_FILE_NOT_FOUND, fileId);
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_FILE_NOT_FOUND, fileId);
      }
      throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_STORAGE_UNAVAILABLE);
    } catch (SdkException e) {
      throw new GeneralException(FileStoreExceptionStatus.FILE_STORE_STORAGE_UNAVAILABLE);
    }
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
}

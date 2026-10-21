# file-store

Files for any module, in buckets of an S3-compatible store (SeaweedFS locally, any S3 elsewhere).
A module keeps only a file's id; what the file is (name, type, checksum, upload time) travels with
the object as its metadata. The type is read from the file's own bytes (PDF, PNG, JPEG; never SVG),
and a file read back must still match its checksum. The buckets are infrastructure's: the app only
checks at startup that each one exists.

```groovy
implementation libs.ecom.file.store
```

```java
var stored = files.store("partner-documents", multipartFile);
var content = files.read("partner-documents", stored.id());
files.delete("partner-documents", stored.id());
```

## Properties

| Property | Default | What |
|---|---|---|
| `ecom.file-store.storage.endpoint` | — | the store's URL; setting it turns the library on |
| `ecom.file-store.storage.region` | — | the region requests are signed for |
| `ecom.file-store.storage.credentials` | `keys` | `keys`: the access and secret key, both required; `default-chain`: AWS's default credentials chain (an instance or container role), only when chosen |
| `ecom.file-store.storage.access-key` | — | the app's access key |
| `ecom.file-store.storage.secret-key` | — | its secret key, from Vault |
| `ecom.file-store.storage.path-style` | false | the bucket in the path, as SeaweedFS and MinIO want |
| `ecom.file-store.storage.api-call-timeout` | 30s | one call, retries included |
| `ecom.file-store.storage.api-call-attempt-timeout` | 10s | one attempt of a call |
| `ecom.file-store.buckets` | — | the buckets the app may use: `<name>.allowed-types` and `<name>.max-size` |

## Errors

| Code | Status | When |
|---|---|---|
| `FILE_STORE_FILE_EMPTY` | 400 | the file is empty |
| `FILE_STORE_FILE_TOO_LARGE` | 413 | larger than the bucket's `max-size` |
| `FILE_STORE_TYPE_NOT_ALLOWED` | 415 | its bytes aren't one of the bucket's types |
| `FILE_STORE_FILE_NOT_FOUND` | 404 | no such file |
| `FILE_STORE_BUCKET_UNKNOWN` | 500 | the code named a bucket that isn't configured |
| `FILE_STORE_FILE_CORRUPT` | 500 | the stored bytes no longer match the checksum |
| `FILE_STORE_STORAGE_UNAVAILABLE` | 503 | the store didn't answer |

## Health

With the actuator, `fileStore`: UP while every configured bucket answers a HeadBucket with the app's
credentials (3 s limit). DOWN lists each failing bucket with the store's error code, or its HTTP
status (403: the signature or key is rejected; 404: no such bucket). Details show only to callers
who may see them (`management.endpoint.health.show-details`).

## Beans you can replace

`S3Client`, `FileStore`.

## Tests

- `./gradlew -p platform :file-store:test`: types from bytes, the rules, a missing bucket, a corrupt
  file, the store down.
- `./gradlew -p platform :file-store:integrationTest`: a real SeaweedFS as an identity that may only
  read and write its buckets (nothing lists them): store, read, delete, rules, a missing bucket, a file changed
  behind the store's back.

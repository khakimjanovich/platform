package uz.heeypay.ecom.filestore;

import uz.heeypay.ecom.error.StatusThrowable;

public enum FileStoreExceptionStatus implements StatusThrowable {
  FILE_STORE_BUCKET_UNKNOWN,
  FILE_STORE_FILE_EMPTY,
  FILE_STORE_FILE_TOO_LARGE,
  FILE_STORE_TYPE_NOT_ALLOWED,
  FILE_STORE_FILE_NOT_FOUND,
  FILE_STORE_STORAGE_UNAVAILABLE,
  FILE_STORE_FILE_CORRUPT;

  @Override
  public String getType() {
    return name();
  }
}

package uz.heeypay.ecom.filestore;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FileStoreConstants {

  public static final String PROPERTIES_PREFIX = "ecom.file-store";

  /** Stored with each object, so the store alone knows what a file is. */
  public static final String FILE_NAME_METADATA = "file-name";

  public static final String SHA256_METADATA = "sha256";
  public static final String UPLOADED_AT_METADATA = "uploaded-at";

  public static final String PDF = "application/pdf";
  public static final String PNG = "image/png";
  public static final String JPEG = "image/jpeg";

  /** Only the name's last part, without anything that could be a path or a header trick. */
  public static final String UNSAFE_NAME_CHARACTERS = "[^\\p{L}\\p{N} ._()-]";

  public static final int MAX_NAME_LENGTH = 200;
  public static final String LOG_STORED = "Stored {} in {} | {} bytes, {}";
}

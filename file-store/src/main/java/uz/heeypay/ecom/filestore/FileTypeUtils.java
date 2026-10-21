package uz.heeypay.ecom.filestore;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import javax.imageio.ImageIO;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * A file's type from its own bytes: the format's signature, and then the file must really be that
 * format (an image that decodes, a PDF that ends). Anything else, or a file that also hides a ZIP
 * (a polyglot), has no type.
 */
@UtilityClass
public class FileTypeUtils {

  private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PDF_END = "%%EOF".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] ZIP_END = {'P', 'K', 5, 6};

  public static Optional<String> detect(byte[] bytes) {
    if (indexOf(bytes, ZIP_END, 0) >= 0) return Optional.empty();
    if (startsWith(bytes, PDF_SIGNATURE)) {
      return indexOf(bytes, PDF_END, Math.max(0, bytes.length - 1024)) >= 0
          ? Optional.of(FileStoreConstants.PDF)
          : Optional.empty();
    }
    if (startsWith(bytes, PNG_SIGNATURE) && decodes(bytes)) {
      return Optional.of(FileStoreConstants.PNG);
    }
    if (startsWith(bytes, JPEG_SIGNATURE) && decodes(bytes)) {
      return Optional.of(FileStoreConstants.JPEG);
    }
    return Optional.empty();
  }

  /** Keeps a readable name for display; nothing that could be a path or break a header. */
  public static String safeName(@Nullable String original) {
    var name = original == null ? "" : original;
    name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    name = name.replaceAll(FileStoreConstants.UNSAFE_NAME_CHARACTERS, "_").strip();
    if (name.isEmpty()) name = "file";
    return name.length() > FileStoreConstants.MAX_NAME_LENGTH
        ? name.substring(name.length() - FileStoreConstants.MAX_NAME_LENGTH)
        : name;
  }

  private static boolean decodes(byte[] bytes) {
    try {
      return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean startsWith(byte[] bytes, byte[] prefix) {
    return bytes.length >= prefix.length
        && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
  }

  private static int indexOf(byte[] bytes, byte[] needle, int from) {
    outer:
    for (var i = from; i <= bytes.length - needle.length; i++) {
      for (var j = 0; j < needle.length; j++) {
        if (bytes[i + j] != needle[j]) continue outer;
      }
      return i;
    }
    return -1;
  }
}

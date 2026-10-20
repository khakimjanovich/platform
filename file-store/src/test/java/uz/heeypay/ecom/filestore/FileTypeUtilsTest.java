package uz.heeypay.ecom.filestore;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class FileTypeUtilsTest {

  @Test
  void theTypeComesFromTheBytesNotTheName() throws Exception {
    assertThat(FileTypeUtils.detect(pdf())).contains("application/pdf");
    assertThat(FileTypeUtils.detect(image("png"))).contains("image/png");
    assertThat(FileTypeUtils.detect(image("jpg"))).contains("image/jpeg");
    assertThat(FileTypeUtils.detect("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)))
        .isEmpty();
  }

  @Test
  void aFakeOrHalfFileHasNoType() throws Exception {
    var png = image("png");
    var truncated = java.util.Arrays.copyOf(png, 20);
    assertThat(FileTypeUtils.detect(truncated)).as("PNG signature, not a PNG").isEmpty();
    assertThat(FileTypeUtils.detect("%PDF-1.7 no end".getBytes(StandardCharsets.US_ASCII)))
        .isEmpty();
    var polyglot = new ByteArrayOutputStream();
    polyglot.write(pdf());
    polyglot.write(new byte[] {'P', 'K', 5, 6, 0, 0});
    assertThat(FileTypeUtils.detect(polyglot.toByteArray())).as("a PDF hiding a ZIP").isEmpty();
  }

  @Test
  void namesKeepOnlyTheirLastPartAndSafeCharacters() {
    assertThat(FileTypeUtils.safeName("../../etc/passwd")).isEqualTo("passwd");
    assertThat(FileTypeUtils.safeName("C:\\docs\\Ustav \"2026\".pdf"))
        .isEqualTo("Ustav _2026_.pdf");
    assertThat(FileTypeUtils.safeName("Устав\r\n.pdf")).isEqualTo("Устав__.pdf");
    assertThat(FileTypeUtils.safeName(null)).isEqualTo("file");
  }

  static byte[] pdf() {
    return "%PDF-1.4\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n"
        .getBytes(StandardCharsets.US_ASCII);
  }

  static byte[] image(String format) throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), format, out);
    return out.toByteArray();
  }
}

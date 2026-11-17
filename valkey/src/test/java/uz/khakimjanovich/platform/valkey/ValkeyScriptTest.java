package uz.khakimjanovich.platform.valkey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class ValkeyScriptTest {

  @Test
  void createsScriptFromString() {
    var script = ValkeyScript.of("return redis.call('ping')", String.class);
    assertThat(script.target()).isNotNull();
    assertThat(script.target().getResultType()).isEqualTo(String.class);
  }

  @Test
  void createsScriptFromResource() {
    var script = ValkeyScript.of(new ByteArrayResource("return 1".getBytes()), Long.class);
    assertThat(script.target()).isNotNull();
    assertThat(script.target().getResultType()).isEqualTo(Long.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void createsScriptFromClassPath() {
    var script =
        ValkeyScript.ofClassPath(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
            (Class<List<String>>) (Class<?>) List.class);
    assertThat(script.target()).isNotNull();
    assertThat(script.target().getResultType()).isEqualTo(List.class);
  }
}

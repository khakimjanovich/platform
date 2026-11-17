package uz.khakimjanovich.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

class IdempotencyTest {

  @Test
  void aNameThatIsNotAPlainIdentifierIsRefused() {
    assertThatThrownBy(() -> idempotency("note; DROP TABLE x", "note_idempotency"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> idempotency("note", "Note-Idempotency"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void defaultInProgressTimeoutIsOneMinute() {
    var instance = idempotency("note", "note_idempotency");
    assertThat(instance.inProgressTimeout()).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void customInProgressTimeoutIsPreserved() {
    var custom =
        new Idempotency(
            "note",
            "note_idempotency",
            mock(DataSource.class),
            mock(PlatformTransactionManager.class),
            JsonMapper.shared(),
            null,
            Duration.ofSeconds(45));
    assertThat(custom.inProgressTimeout()).isEqualTo(Duration.ofSeconds(45));
  }

  @Test
  void nullInProgressTimeoutOrClockIsRefused() {
    var ds = mock(DataSource.class);
    var tm = mock(PlatformTransactionManager.class);
    var json = JsonMapper.shared();
    assertThatThrownBy(
            () ->
                new Idempotency(
                    "note", "note_idempotency", ds, tm, json, null, null, Clock.systemUTC()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new Idempotency(
                    "note", "note_idempotency", ds, tm, json, null, Duration.ofMinutes(1), null))
        .isInstanceOf(NullPointerException.class);
  }

  private static Idempotency idempotency(String schema, String table) {
    return new Idempotency(
        schema,
        table,
        mock(DataSource.class),
        mock(PlatformTransactionManager.class),
        JsonMapper.shared());
  }
}

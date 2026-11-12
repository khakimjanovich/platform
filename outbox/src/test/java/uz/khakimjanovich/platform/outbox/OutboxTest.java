package uz.khakimjanovich.platform.outbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class OutboxTest {

  @Test
  void aNameThatIsNotAPlainIdentifierIsRefused() {
    assertThatThrownBy(() -> outbox("note; DROP TABLE x", "note_outbox", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> outbox("note", "Note-Outbox", 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRelayBatchBelowOneIsRefused() {
    assertThatThrownBy(() -> outbox("note", "note_outbox", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @SuppressWarnings("unchecked")
  private static Outbox outbox(String schema, String table, int batch) {
    return new Outbox(
        schema,
        table,
        mock(DataSource.class),
        mock(PlatformTransactionManager.class),
        mock(KafkaTemplate.class),
        batch,
        Tracer.NOOP,
        Propagator.NOOP);
  }
}

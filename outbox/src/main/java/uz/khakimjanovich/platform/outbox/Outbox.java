package uz.khakimjanovich.platform.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One module's outbox: a table in its own schema (the changelog {@code
 * db/changelog/platform-outbox.yaml}), written in the module's transaction, so a change and its
 * event commit together or not at all; and the relay that moves it to Kafka.
 *
 * <p>A row is marked published only after Kafka has it; a crash in between publishes it again, so
 * consumers see each event at least once and drop repeats by its id. Events with one key keep the
 * order they were written in. The module builds one per outbox table and calls {@link #relay()} on
 * its own schedule, in the apps that should publish.
 */
public class Outbox {

  private final JdbcClient jdbc;
  private final TransactionTemplate transaction;
  private final KafkaTemplate<String, String> kafka;
  private final int batch;
  private final Tracer tracer;
  private final Propagator propagator;
  private final String insert;
  private final String lockUnpublished;
  private final String markPublished;

  /**
   * @param schema the module's schema
   * @param table the outbox table, as the module named it in its changelog
   * @param dataSource the module's pool, the one its transaction manager runs on
   * @param transactionManager the module's, for the relay's batches
   * @param batch how many events the relay takes in one transaction
   * @param tracer the app's, or {@link Tracer#NOOP}
   * @param propagator the app's, or {@link Propagator#NOOP}
   */
  public Outbox(
      String schema,
      String table,
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      KafkaTemplate<String, String> kafka,
      int batch,
      Tracer tracer,
      Propagator propagator) {
    if (batch < 1) {
      throw new IllegalArgumentException(OutboxConstants.BATCH_INVALID.formatted(batch));
    }
    this.jdbc = JdbcClient.create(dataSource);
    this.transaction = new TransactionTemplate(transactionManager);
    this.kafka = kafka;
    this.batch = batch;
    this.tracer = tracer;
    this.propagator = propagator;
    var target = name(schema) + "." + name(table);
    this.insert = OutboxConstants.INSERT.formatted(target);
    this.lockUnpublished = OutboxConstants.LOCK_UNPUBLISHED.formatted(target);
    this.markPublished = OutboxConstants.MARK_PUBLISHED.formatted(target);
  }

  /**
   * Writes the event in the caller's transaction, with the current trace, so the relay publishes it
   * inside the trace of the request that wrote it. Without a transaction it commits on its own.
   */
  public void append(OutboxEvent event) {
    jdbc.sql(insert)
        .param("id", event.id())
        .param("topic", event.topic())
        .param("key", event.key())
        .param("type", event.type())
        .param("payload", event.payload())
        .param("traceparent", traceparent())
        .update();
  }

  /** Publishes every unpublished event, a batch per transaction, oldest first. */
  public void relay() {
    Integer published;
    do {
      published = transaction.execute(status -> publishBatch());
    } while (published != null && published == batch);
  }

  private int publishBatch() {
    var rows =
        jdbc.sql(lockUnpublished)
            .param("limit", batch)
            .query(
                (rs, n) ->
                    new Row(
                        rs.getObject("id", UUID.class),
                        rs.getString("topic"),
                        rs.getString("event_key"),
                        rs.getString("type"),
                        rs.getString("payload"),
                        rs.getString("traceparent")))
            .list();
    if (rows.isEmpty()) return 0;
    var sends = new ArrayList<CompletableFuture<?>>();
    for (var row : rows) {
      // Sent inside the trace of the request that wrote the event, which Kafka then carries on.
      var span = span(row.traceparent());
      try (var _ = tracer.withSpan(span)) {
        sends.add(kafka.send(record(row)));
      } finally {
        span.end();
      }
    }
    CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
    jdbc.sql(markPublished).param("ids", rows.stream().map(Row::id).toList()).update();
    return rows.size();
  }

  private static ProducerRecord<String, String> record(Row row) {
    return new ProducerRecord<>(
        row.topic(),
        null,
        row.key(),
        row.payload(),
        List.of(
            new RecordHeader(
                OutboxConstants.HEADER_EVENT_ID,
                row.id().toString().getBytes(StandardCharsets.UTF_8)),
            new RecordHeader(
                OutboxConstants.HEADER_EVENT_TYPE, row.type().getBytes(StandardCharsets.UTF_8))));
  }

  private Span span(@Nullable String traceparent) {
    var builder =
        traceparent == null
            ? tracer.spanBuilder()
            : propagator.extract(Map.of(OutboxConstants.TRACEPARENT, traceparent), Map::get);
    return builder.name(OutboxConstants.RELAY_SPAN).start();
  }

  /** The current trace as a W3C traceparent, or null when nothing is traced. */
  private @Nullable String traceparent() {
    var context = tracer.currentTraceContext().context();
    if (context == null) return null;
    var carrier = new HashMap<String, String>();
    propagator.inject(context, carrier, (same, key, value) -> carrier.put(key, value));
    return carrier.get(OutboxConstants.TRACEPARENT);
  }

  private static String name(String name) {
    if (!OutboxConstants.NAME.matcher(name).matches()) {
      throw new IllegalArgumentException(OutboxConstants.NAME_INVALID.formatted(name));
    }
    return name;
  }

  private record Row(
      UUID id,
      String topic,
      String key,
      String type,
      String payload,
      @Nullable String traceparent) {}
}

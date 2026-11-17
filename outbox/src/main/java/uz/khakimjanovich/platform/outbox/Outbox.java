package uz.khakimjanovich.platform.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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

public class Outbox {

  private final JdbcClient jdbc;
  private final TransactionTemplate transaction;
  private final KafkaTemplate<String, String> kafka;
  private final int batch;
  private final Tracer tracer;
  private final Propagator propagator;
  private final String target;
  private final String insert;
  private final String lockUnpublished;
  private final String markPublished;
  private final String purgePublished;

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
    this.target = name(schema) + "." + name(table);
    this.insert = OutboxConstants.INSERT.formatted(target);
    this.lockUnpublished = OutboxConstants.LOCK_UNPUBLISHED.formatted(target);
    this.markPublished = OutboxConstants.MARK_PUBLISHED.formatted(target);
    this.purgePublished = OutboxConstants.PURGE_PUBLISHED.formatted(target);
  }

  public void append(OutboxEvent event) {
    append(event, traceparent());
  }

  public void append(OutboxEvent event, @Nullable String traceparent) {
    jdbc.sql(insert)
        .param("id", event.id())
        .param("topic", event.topic())
        .param("key", event.key())
        .param("type", event.type())
        .param("payload", event.payload())
        .param("traceparent", traceparent)
        .update();
  }

  public void relay() {
    Integer published;
    do {
      published = transaction.execute(status -> publishBatch());
    } while (published != null && published == batch);
  }

  public int purgePublishedBefore(Instant before) {
    return jdbc.sql(purgePublished).param("before", Timestamp.from(before)).update();
  }

  private int publishBatch() {
    // One relay at a time per outbox, across replicas: two would publish one key's events out of
    // order. The lock goes with the transaction.
    var mine =
        jdbc.sql(OutboxConstants.TRY_RELAY_LOCK)
            .param("outbox", target)
            .query(Boolean.class)
            .single();
    if (!Boolean.TRUE.equals(mine)) return 0;
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
        var record = record(row);
        injectTrace(record, span, row.traceparent());
        sends.add(kafka.send(record));
      } finally {
        span.end();
      }
    }
    CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
    jdbc.sql(markPublished).param("ids", rows.stream().map(Row::id).toList()).update();
    return rows.size();
  }

  private static ProducerRecord<String, String> record(Row row) {
    var record = new ProducerRecord<String, String>(row.topic(), null, row.key(), row.payload());
    record
        .headers()
        .add(
            new RecordHeader(
                OutboxConstants.HEADER_EVENT_ID,
                row.id().toString().getBytes(StandardCharsets.UTF_8)))
        .add(
            new RecordHeader(
                OutboxConstants.HEADER_EVENT_TYPE, row.type().getBytes(StandardCharsets.UTF_8)));
    return record;
  }

  private void injectTrace(
      ProducerRecord<String, String> record, Span span, @Nullable String traceparent) {
    var context = span.context();
    if (context != null && context.traceId() != null && !context.traceId().isBlank()) {
      propagator.inject(
          context,
          record.headers(),
          (same, key, value) ->
              record.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8))));
      if (record.headers().lastHeader(OutboxConstants.HEADER_REQUEST_ID) == null) {
        record
            .headers()
            .add(
                new RecordHeader(
                    OutboxConstants.HEADER_REQUEST_ID,
                    context.traceId().getBytes(StandardCharsets.UTF_8)));
      }
    }
    if (traceparent != null && record.headers().lastHeader(OutboxConstants.TRACEPARENT) == null) {
      record
          .headers()
          .add(
              new RecordHeader(
                  OutboxConstants.TRACEPARENT, traceparent.getBytes(StandardCharsets.UTF_8)));
    }
    if (traceparent != null
        && record.headers().lastHeader(OutboxConstants.HEADER_REQUEST_ID) == null) {
      var traceId = extractTraceId(traceparent);
      if (traceId != null) {
        record
            .headers()
            .add(
                new RecordHeader(
                    OutboxConstants.HEADER_REQUEST_ID, traceId.getBytes(StandardCharsets.UTF_8)));
      }
    }
  }

  private static @Nullable String extractTraceId(String traceparent) {
    if (traceparent.length() == 55
        && traceparent.startsWith("00-")
        && traceparent.charAt(35) == '-') {
      return traceparent.substring(3, 35);
    }
    return null;
  }

  private Span span(@Nullable String traceparent) {
    var builder =
        traceparent == null
            ? tracer.spanBuilder()
            : propagator.extract(Map.of(OutboxConstants.TRACEPARENT, traceparent), Map::get);
    return builder.name(OutboxConstants.RELAY_SPAN).start();
  }

  private @Nullable String traceparent() {
    var context = tracer.currentTraceContext().context();
    if (context == null) return null;
    var carrier = new HashMap<String, String>();
    propagator.inject(context, carrier, (same, key, value) -> carrier.put(key, value));
    var injected = carrier.get(OutboxConstants.TRACEPARENT);
    if (injected != null && !injected.isBlank()) {
      return injected;
    }
    if (context.traceId() != null
        && !context.traceId().isBlank()
        && context.spanId() != null
        && !context.spanId().isBlank()) {
      return "00-"
          + context.traceId()
          + "-"
          + context.spanId()
          + "-"
          + (Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
    }
    return null;
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

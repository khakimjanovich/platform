package uz.khakimjanovich.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uz.khakimjanovich.platform.persistence.ModulePersistence;

/**
 * A module's outbox on a real Postgres and Kafka, wired as a module wires it: its changelog
 * includes the platform's, and its JPA transaction manager carries the outbox writes.
 */
@Testcontainers
class OutboxIT {

  static final String TOPIC = "note-events";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(ResourceBundle.getBundle("images").getString("postgres"));

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(ResourceBundle.getBundle("images").getString("kafka"));

  static HikariDataSource dataSource;
  static PlatformTransactionManager transactionManager;
  static KafkaConsumer<String, String> consumer;

  Outbox outbox;

  @BeforeAll
  static void migrate() throws Exception {
    dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    JdbcClient.create(dataSource).sql("CREATE SCHEMA note").update();
    var liquibase = ModulePersistence.liquibase("note", "db/changelog/note.yaml", dataSource);
    liquibase.setResourceLoader(new DefaultResourceLoader());
    liquibase.afterPropertiesSet();
    var factory = ModulePersistence.entityManagerFactory("note", OutboxIT.class, dataSource);
    factory.afterPropertiesSet();
    transactionManager =
        ModulePersistence.transactionManager(Objects.requireNonNull(factory.getObject()));
    consumer =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
            new StringDeserializer(),
            new StringDeserializer());
    consumer.subscribe(List.of(TOPIC));
  }

  @AfterAll
  static void close() {
    consumer.close();
    dataSource.close();
  }

  @BeforeEach
  void setUp() {
    JdbcClient.create(dataSource).sql("DELETE FROM note.note_outbox").update();
    outbox = outbox(KAFKA.getBootstrapServers(), 2);
  }

  @Test
  void anEventCommitsWithItsTransactionOrNotAtAll() {
    var kept = event("k1", "kept");
    var transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(status -> outbox.append(kept));
    transaction.executeWithoutResult(
        status -> {
          outbox.append(event("k1", "rolled back"));
          status.setRollbackOnly();
        });
    outbox.relay();

    var records = poll(1);
    assertThat(records).extracting(ConsumerRecord::value).containsExactly("kept");
    var record = records.getFirst();
    assertThat(record.key()).isEqualTo("k1");
    assertThat(header(record, OutboxConstants.HEADER_EVENT_ID)).isEqualTo(kept.id().toString());
    assertThat(header(record, OutboxConstants.HEADER_EVENT_TYPE)).isEqualTo("noted");
  }

  @Test
  void oneKeysEventsArriveInTheOrderWrittenAndOnlyOnce() {
    for (int i = 0; i < 5; i++) outbox.append(event("k2", "e" + i));
    outbox.relay();
    outbox.relay();

    assertThat(poll(5))
        .extracting(ConsumerRecord::value)
        .containsExactly("e0", "e1", "e2", "e3", "e4");
    assertThat(unpublished()).isZero();
  }

  @Test
  void anEventKafkaNeverTookStaysUnpublished() throws Exception {
    int closed;
    try (var socket = new ServerSocket(0)) {
      closed = socket.getLocalPort();
    }
    var down = outbox("localhost:" + closed, 2);
    down.append(event("k3", "waiting"));
    assertThatThrownBy(down::relay).isInstanceOf(RuntimeException.class);
    assertThat(unpublished()).isOne();
  }

  private static Outbox outbox(String servers, int batch) {
    return new Outbox(
        "note",
        "note_outbox",
        dataSource,
        transactionManager,
        template(servers),
        batch,
        Tracer.NOOP,
        Propagator.NOOP);
  }

  private static KafkaTemplate<String, String> template(String servers) {
    return new KafkaTemplate<>(
        new DefaultKafkaProducerFactory<>(
            Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 3000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000),
            new StringSerializer(),
            new StringSerializer()));
  }

  private static OutboxEvent event(String key, String payload) {
    return new OutboxEvent(UUID.randomUUID(), TOPIC, key, "noted", payload);
  }

  private static List<ConsumerRecord<String, String>> poll(int count) {
    var records = new ArrayList<ConsumerRecord<String, String>>();
    var until = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (records.size() < count && System.nanoTime() < until) {
      consumer.poll(Duration.ofMillis(500)).forEach(records::add);
    }
    return records;
  }

  private static String header(ConsumerRecord<String, String> record, String name) {
    return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }

  private static long unpublished() {
    return JdbcClient.create(dataSource)
        .sql("SELECT count(*) FROM note.note_outbox WHERE published_at IS NULL")
        .query(Long.class)
        .single();
  }
}

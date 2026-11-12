package uz.khakimjanovich.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import uz.khakimjanovich.platform.error.GeneralException;
import uz.khakimjanovich.platform.persistence.ModulePersistence;

/**
 * A module's idempotency table on a real Postgres, wired as a module wires it: its changelog
 * includes the platform's, and its JPA transaction manager carries the work and the record.
 */
@Testcontainers
class IdempotencyIT {

  static final String CLIENT = "client-1";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(ResourceBundle.getBundle("images").getString("postgres"));

  static HikariDataSource dataSource;
  static PlatformTransactionManager transactionManager;
  static JdbcClient jdbc;

  Idempotency idempotency;
  final AtomicInteger runs = new AtomicInteger();

  record Answer(UUID id) {}

  @BeforeAll
  static void migrate() {
    dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    jdbc = JdbcClient.create(dataSource);
    jdbc.sql("CREATE SCHEMA note").update();
    var liquibase = ModulePersistence.liquibase("note", "db/changelog/note.yaml", dataSource);
    liquibase.setResourceLoader(new DefaultResourceLoader());
    try {
      liquibase.afterPropertiesSet();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    var factory = ModulePersistence.entityManagerFactory("note", IdempotencyIT.class, dataSource);
    factory.afterPropertiesSet();
    transactionManager =
        ModulePersistence.transactionManager(Objects.requireNonNull(factory.getObject()));
  }

  @AfterAll
  static void close() {
    dataSource.close();
  }

  @BeforeEach
  void setUp() {
    jdbc.sql("DELETE FROM note.note_idempotency").update();
    jdbc.sql("DELETE FROM note.note").update();
    idempotency =
        new Idempotency(
            "note", "note_idempotency", dataSource, transactionManager, JsonMapper.shared());
  }

  @Test
  void aRetryGetsTheFirstAnswerAndTheWorkRunsOnce() {
    var first = idempotency.execute(CLIENT, "k1", "h1", Answer.class, this::note);
    var retry = idempotency.execute(CLIENT, "k1", "h1", Answer.class, this::note);

    assertThat(first.replayed()).isFalse();
    assertThat(retry.replayed()).isTrue();
    assertThat(retry.response()).isEqualTo(first.response());
    assertThat(runs).hasValue(1);
  }

  @Test
  void aKeyReusedForADifferentRequestIsRefused() {
    idempotency.execute(CLIENT, "k2", "h1", Answer.class, this::note);
    assertThatThrownBy(() -> idempotency.execute(CLIENT, "k2", "h2", Answer.class, this::note))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus())
                    .isEqualTo(IdempotencyExceptionStatus.IDEMPOTENCY_KEY_REUSED));
  }

  @Test
  void twoClientsMayUseOneKey() {
    idempotency.execute(CLIENT, "k3", "h1", Answer.class, this::note);
    var other = idempotency.execute("client-2", "k3", "h2", Answer.class, this::note);
    assertThat(other.replayed()).isFalse();
  }

  @Test
  void failedWorkKeepsNoRecordSoTheRetryRunsIt() {
    assertThatThrownBy(
            () ->
                idempotency.execute(
                    CLIENT,
                    "k4",
                    "h1",
                    Answer.class,
                    () -> {
                      note();
                      throw new IllegalStateException("refused");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count("note.note")).isZero();

    assertThat(idempotency.execute(CLIENT, "k4", "h1", Answer.class, this::note).replayed())
        .isFalse();
  }

  @Test
  void concurrentRetriesRunTheWorkOnceAndReplayIt() throws Exception {
    var replies = new ArrayList<Future<Idempotency.Result<Answer>>>();
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      for (int i = 0; i < 10; i++) {
        replies.add(
            pool.submit(
                () -> {
                  start.await();
                  return idempotency.execute(CLIENT, "k5", "h1", Answer.class, this::note);
                }));
      }
      start.countDown();
    }
    var first = new ArrayList<Idempotency.Result<Answer>>();
    for (var reply : replies) {
      if (!reply.get().replayed()) first.add(reply.get());
    }
    assertThat(first).hasSize(1);
    assertThat(count("note.note")).isOne();
    for (var reply : replies) {
      assertThat(reply.get().response()).isEqualTo(first.getFirst().response());
    }
  }

  @Test
  void purgingForgetsOldKeys() {
    idempotency.execute(CLIENT, "k6", "h1", Answer.class, this::note);
    assertThat(idempotency.purgeBefore(Instant.now().minus(Duration.ofHours(1)))).isZero();
    assertThat(idempotency.purgeBefore(Instant.now().plus(Duration.ofHours(1)))).isOne();
    assertThat(idempotency.execute(CLIENT, "k6", "h2", Answer.class, this::note).replayed())
        .isFalse();
  }

  /** The module's own write, in the same transaction as the record. */
  private Answer note() {
    runs.incrementAndGet();
    var id = UUID.randomUUID();
    jdbc.sql("INSERT INTO note.note (id) VALUES (:id)").param("id", id).update();
    return new Answer(id);
  }

  private static long count(String table) {
    return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
  }
}

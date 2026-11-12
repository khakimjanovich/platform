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
import java.util.concurrent.ExecutionException;
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
import uz.khakimjanovich.platform.error.StatusThrowable;
import uz.khakimjanovich.platform.persistence.ModulePersistence;

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
  void aRefusalFreesTheKeySoTheRetryRunsIt() {
    StatusThrowable refused = () -> "REFUSED";
    assertThatThrownBy(
            () ->
                idempotency.execute(
                    CLIENT,
                    "k4",
                    "h1",
                    Answer.class,
                    () -> {
                      runs.incrementAndGet();
                      throw new GeneralException(refused);
                    }))
        .isInstanceOf(GeneralException.class);

    assertThat(idempotency.execute(CLIENT, "k4", "h1", Answer.class, this::note).replayed())
        .isFalse();
    assertThat(runs).hasValue(2);
  }

  @Test
  void workThatEndsWithoutAnAnswerIsNeverRunAgain() {
    assertThatThrownBy(
            () ->
                idempotency.execute(
                    CLIENT,
                    "k7",
                    "h1",
                    Answer.class,
                    () -> {
                      runs.incrementAndGet();
                      throw new IllegalStateException("no answer in time");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> idempotency.execute(CLIENT, "k7", "h1", Answer.class, this::note))
        .isInstanceOfSatisfying(
            GeneralException.class,
            e ->
                assertThat(e.getStatus())
                    .isEqualTo(IdempotencyExceptionStatus.IDEMPOTENCY_IN_PROGRESS));
    assertThat(runs).hasValue(1);
  }

  @Test
  void concurrentRequestsWithOneKeyRunTheWorkOnce() throws Exception {
    var replies = new ArrayList<Future<Idempotency.Result<Answer>>>();
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      for (int i = 0; i < 10; i++) {
        replies.add(
            pool.submit(
                () -> {
                  start.await();
                  return idempotency.execute(CLIENT, "k5", "h1", Answer.class, this::slowNote);
                }));
      }
      start.countDown();
    }
    var answered = new ArrayList<Idempotency.Result<Answer>>();
    var inProgress = 0;
    for (var reply : replies) {
      try {
        answered.add(reply.get());
      } catch (ExecutionException e) {
        assertThat(e.getCause())
            .isInstanceOfSatisfying(
                GeneralException.class,
                g ->
                    assertThat(g.getStatus())
                        .isEqualTo(IdempotencyExceptionStatus.IDEMPOTENCY_IN_PROGRESS));
        inProgress++;
      }
    }
    assertThat(runs).as("the work itself").hasValue(1);
    assertThat(answered.stream().filter(r -> !r.replayed())).hasSize(1);
    assertThat(answered.size() + inProgress).isEqualTo(10);
  }

  @Test
  void aKeyOlderThanItsKeepIsFreeAgain() {
    var kept =
        new Idempotency(
            "note",
            "note_idempotency",
            dataSource,
            transactionManager,
            JsonMapper.shared(),
            Duration.ofHours(1));
    kept.execute(CLIENT, "k8", "h1", Answer.class, this::note);
    jdbc.sql("UPDATE note.note_idempotency SET created_at = now() - interval '2 hours'").update();

    assertThat(kept.execute(CLIENT, "k8", "h2", Answer.class, this::note).replayed()).isFalse();
    assertThat(runs).hasValue(2);
  }

  @Test
  void purgingForgetsOldKeys() {
    idempotency.execute(CLIENT, "k6", "h1", Answer.class, this::note);
    assertThat(idempotency.purgeBefore(Instant.now().minus(Duration.ofHours(1)))).isZero();
    assertThat(idempotency.purgeBefore(Instant.now().plus(Duration.ofHours(1)))).isOne();
    assertThat(idempotency.execute(CLIENT, "k6", "h2", Answer.class, this::note).replayed())
        .isFalse();
  }

  private Answer note() {
    runs.incrementAndGet();
    var id = UUID.randomUUID();
    jdbc.sql("INSERT INTO note.note (id) VALUES (:id)").param("id", id).update();
    return new Answer(id);
  }

  private Answer slowNote() {
    try {
      Thread.sleep(300);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return note();
  }
}

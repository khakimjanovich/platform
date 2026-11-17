package uz.khakimjanovich.platform.idempotency;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import uz.khakimjanovich.platform.error.GeneralException;

public class Idempotency {

  private final JdbcClient jdbc;
  private final TransactionTemplate own;
  private final JsonMapper json;
  private final @Nullable Duration keep;
  private final Duration inProgressTimeout;
  private final Clock clock;
  private final String find;
  private final String claim;
  private final String answer;
  private final String release;
  private final String expire;
  private final String expireInProgress;
  private final String purge;

  public Idempotency(
      String schema,
      String table,
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      JsonMapper json) {
    this(schema, table, dataSource, transactionManager, json, null);
  }

  public Idempotency(
      String schema,
      String table,
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      JsonMapper json,
      @Nullable Duration keep) {
    this(
        schema,
        table,
        dataSource,
        transactionManager,
        json,
        keep,
        IdempotencyConstants.DEFAULT_IN_PROGRESS_TIMEOUT,
        Clock.systemUTC());
  }

  public Idempotency(
      String schema,
      String table,
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      JsonMapper json,
      @Nullable Duration keep,
      Duration inProgressTimeout) {
    this(
        schema,
        table,
        dataSource,
        transactionManager,
        json,
        keep,
        inProgressTimeout,
        Clock.systemUTC());
  }

  public Idempotency(
      String schema,
      String table,
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      JsonMapper json,
      @Nullable Duration keep,
      Duration inProgressTimeout,
      Clock clock) {
    var target = name(schema) + "." + name(table);
    this.jdbc = JdbcClient.create(dataSource);
    // The claim, the answer and the release commit at once, whatever transaction the caller is in:
    // a concurrent request must see them.
    this.own = new TransactionTemplate(transactionManager);
    this.own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.json = json;
    this.keep = keep;
    this.inProgressTimeout =
        Objects.requireNonNull(inProgressTimeout, "inProgressTimeout must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.find = IdempotencyConstants.FIND.formatted(target);
    this.claim = IdempotencyConstants.CLAIM.formatted(target);
    this.answer = IdempotencyConstants.ANSWER.formatted(target);
    this.release = IdempotencyConstants.RELEASE.formatted(target);
    this.expire = IdempotencyConstants.EXPIRE.formatted(target);
    this.expireInProgress = IdempotencyConstants.EXPIRE_IN_PROGRESS.formatted(target);
    this.purge = IdempotencyConstants.PURGE.formatted(target);
  }

  public record Result<T>(@Nullable T response, boolean replayed) {}

  public <T> Result<T> execute(
      String clientId, String key, String requestHash, Class<T> type, Supplier<T> work) {
    if (claim(clientId, key, requestHash) == 0) {
      return earlier(clientId, key, requestHash, type);
    }
    T response;
    try {
      response = work.get();
    } catch (GeneralException refused) {
      // A refusal is an answer the client may correct: the key is free again.
      ownTransaction(() -> jdbc.sql(release).param("client", clientId).param("key", key).update());
      throw refused;
    }
    // Anything else leaves the outcome unknown: the key stays in progress and the work never runs
    // twice.
    ownTransaction(
        () ->
            jdbc.sql(answer)
                .param("client", clientId)
                .param("key", key)
                .param("response", json.writeValueAsString(response))
                .update());
    return new Result<>(response, false);
  }

  public Result<Void> run(String clientId, String key, String requestHash, Runnable work) {
    return execute(
        clientId,
        key,
        requestHash,
        Void.class,
        () -> {
          work.run();
          return null;
        });
  }

  public int purgeBefore(Instant before) {
    return jdbc.sql(purge).param("before", Timestamp.from(before)).update();
  }

  public @Nullable Duration keep() {
    return keep;
  }

  public Duration inProgressTimeout() {
    return inProgressTimeout;
  }

  public Clock clock() {
    return clock;
  }

  private int claim(String clientId, String key, String requestHash) {
    return ownTransaction(
        () -> {
          var now = clock.instant();
          jdbc.sql(expireInProgress)
              .param("client", clientId)
              .param("key", key)
              .param("before", Timestamp.from(now.minus(inProgressTimeout)))
              .update();
          if (keep != null) {
            jdbc.sql(expire)
                .param("client", clientId)
                .param("key", key)
                .param("before", Timestamp.from(now.minus(keep)))
                .update();
          }
          return jdbc.sql(claim)
              .param("client", clientId)
              .param("key", key)
              .param("hash", requestHash)
              .update();
        });
  }

  private <T> Result<T> earlier(String clientId, String key, String requestHash, Class<T> type) {
    var kept =
        jdbc.sql(find)
            .param("client", clientId)
            .param("key", key)
            .query((rs, n) -> new Kept(rs.getString("request_hash"), rs.getString("response")))
            .optional()
            .orElseThrow(
                () -> new GeneralException(IdempotencyExceptionStatus.IDEMPOTENCY_IN_PROGRESS));
    if (!kept.requestHash().equals(requestHash)) {
      throw new GeneralException(IdempotencyExceptionStatus.IDEMPOTENCY_KEY_REUSED);
    }
    if (kept.response() == null) {
      throw new GeneralException(IdempotencyExceptionStatus.IDEMPOTENCY_IN_PROGRESS);
    }
    return new Result<>(json.readValue(kept.response(), type), true);
  }

  private int ownTransaction(Supplier<Integer> statement) {
    return Objects.requireNonNull(own.execute(status -> statement.get()));
  }

  private static String name(String name) {
    if (!IdempotencyConstants.NAME.matcher(name).matches()) {
      throw new IllegalArgumentException(IdempotencyConstants.NAME_INVALID.formatted(name));
    }
    return name;
  }

  private record Kept(String requestHash, @Nullable String response) {}
}

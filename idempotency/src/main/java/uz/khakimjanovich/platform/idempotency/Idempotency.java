package uz.khakimjanovich.platform.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import uz.khakimjanovich.platform.error.GeneralException;

/**
 * One module's idempotency records: a table in its own schema (the changelog {@code
 * db/changelog/platform-idempotency.yaml}) that keeps the first answer to each client's key. The
 * work and its record are saved in one transaction, so a retry finds either both or neither. The
 * request's hash tells a retry from a different request that reuses the key.
 *
 * <p>Two concurrent requests with one key meet at the table's primary key; the loser tries once
 * more and replays the winner's answer. A unique key of the module's own that the work hits is the
 * module's to answer: on the second try its exception goes to the caller.
 */
public class Idempotency {

  private final JdbcClient jdbc;
  private final TransactionTemplate transaction;
  private final JsonMapper json;
  private final String find;
  private final String insert;
  private final String purge;

  /**
   * @param schema the module's schema
   * @param table the idempotency table, as the module named it in its changelog
   * @param dataSource the module's pool, the one its transaction manager runs on
   * @param transactionManager the module's: the work and its record commit together
   */
  public Idempotency(
      String schema,
      String table,
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      JsonMapper json) {
    var target = name(schema) + "." + name(table);
    this.jdbc = JdbcClient.create(dataSource);
    this.transaction = new TransactionTemplate(transactionManager);
    this.json = json;
    this.find = IdempotencyConstants.FIND.formatted(target);
    this.insert = IdempotencyConstants.INSERT.formatted(target);
    this.purge = IdempotencyConstants.PURGE.formatted(target);
  }

  /** The answer, and whether it is the kept first answer to an earlier request. */
  public record Result<T>(T response, boolean replayed) {}

  /**
   * Runs the work once per client and key, in one transaction with its record; a later call with
   * the same key and hash gets the first answer back. Keys are the client's own, so two clients may
   * use the same one.
   *
   * @throws GeneralException {@code IDEMPOTENCY_KEY_REUSED} when the key was used for a request
   *     with a different hash
   */
  public <T> Result<T> execute(
      String clientId, String key, String requestHash, Class<T> type, Supplier<T> work) {
    try {
      return attemptInTransaction(clientId, key, requestHash, type, work);
    } catch (DataIntegrityViolationException raced) {
      // A concurrent request won: with the same key the retry replays its answer.
      return attemptInTransaction(clientId, key, requestHash, type, work);
    }
  }

  /** Forgets the keys first used before this time; returns how many. */
  public int purgeBefore(Instant before) {
    return jdbc.sql(purge).param("before", Timestamp.from(before)).update();
  }

  private <T> Result<T> attemptInTransaction(
      String clientId, String key, String requestHash, Class<T> type, Supplier<T> work) {
    return Objects.requireNonNull(
        transaction.execute(status -> attempt(clientId, key, requestHash, type, work)));
  }

  private <T> Result<T> attempt(
      String clientId, String key, String requestHash, Class<T> type, Supplier<T> work) {
    var earlier =
        jdbc.sql(find)
            .param("client", clientId)
            .param("key", key)
            .query((rs, n) -> new Kept(rs.getString("request_hash"), rs.getString("response")))
            .optional();
    if (earlier.isPresent()) {
      if (!earlier.get().requestHash().equals(requestHash)) {
        throw new GeneralException(IdempotencyExceptionStatus.IDEMPOTENCY_KEY_REUSED);
      }
      return new Result<>(json.readValue(earlier.get().response(), type), true);
    }
    var response = work.get();
    jdbc.sql(insert)
        .param("client", clientId)
        .param("key", key)
        .param("hash", requestHash)
        .param("response", json.writeValueAsString(response))
        .update();
    return new Result<>(response, false);
  }

  private static String name(String name) {
    if (!IdempotencyConstants.NAME.matcher(name).matches()) {
      throw new IllegalArgumentException(IdempotencyConstants.NAME_INVALID.formatted(name));
    }
    return name;
  }

  private record Kept(String requestHash, String response) {}
}

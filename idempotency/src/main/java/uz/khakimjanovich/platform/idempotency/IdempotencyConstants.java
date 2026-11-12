package uz.khakimjanovich.platform.idempotency;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class IdempotencyConstants {

  /** A schema or table name the queries may hold: lower-case SQL identifiers only. */
  public static final Pattern NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

  public static final String NAME_INVALID = "Not a lower-case SQL name: '%s'";

  public static final String FIND =
      "SELECT request_hash, response FROM %s WHERE client_id = :client AND idempotency_key = :key";

  public static final String INSERT =
      """
      INSERT INTO %s (client_id, idempotency_key, request_hash, response, created_at)
      VALUES (:client, :key, :hash, :response, now())
      """;

  public static final String PURGE = "DELETE FROM %s WHERE created_at < :before";
}

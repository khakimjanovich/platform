package uz.khakimjanovich.platform.idempotency;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class IdempotencyConstants {

  public static final Pattern NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

  public static final String NAME_INVALID = "Not a lower-case SQL name: '%s'";

  public static final String FIND =
      "SELECT request_hash, response FROM %s WHERE client_id = :client AND idempotency_key = :key";

  public static final String CLAIM =
      """
      INSERT INTO %s (client_id, idempotency_key, request_hash, created_at)
      VALUES (:client, :key, :hash, now())
      ON CONFLICT DO NOTHING
      """;

  public static final String ANSWER =
      "UPDATE %s SET response = :response WHERE client_id = :client AND idempotency_key = :key";

  public static final String RELEASE =
      "DELETE FROM %s WHERE client_id = :client AND idempotency_key = :key";

  public static final String EXPIRE =
      "DELETE FROM %s WHERE client_id = :client AND idempotency_key = :key AND created_at < :before";

  public static final String PURGE = "DELETE FROM %s WHERE created_at < :before";
}

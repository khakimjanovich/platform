package uz.khakimjanovich.platform.outbox;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OutboxConstants {

  public static final Pattern NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

  public static final String NAME_INVALID = "Not a lower-case SQL name: '%s'";
  public static final String BATCH_INVALID = "The relay batch must be positive, not %d";

  public static final String INSERT =
      """
      INSERT INTO %s (id, topic, event_key, type, payload, created_at, traceparent)
      VALUES (:id, :topic, :key, :type, :payload, now(), :traceparent)
      """;

  public static final String LOCK_UNPUBLISHED =
      """
      SELECT id, topic, event_key, type, payload, traceparent FROM %s
      WHERE published_at IS NULL
      ORDER BY seq
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """;

  public static final String TRY_RELAY_LOCK = "SELECT pg_try_advisory_xact_lock(hashtext(:outbox))";
  public static final String MARK_PUBLISHED =
      "UPDATE %s SET published_at = now() WHERE id IN (:ids)";

  public static final String HEADER_EVENT_ID = "event-id";

  public static final String HEADER_EVENT_TYPE = "event-type";

  public static final String TRACEPARENT = "traceparent";
  public static final String RELAY_SPAN = "outbox relay";
}

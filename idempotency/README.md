# idempotency

The first answer to a client's `Idempotency-Key`, kept in a table in the module's own schema and
written in the same transaction as the work, so a retry gets the same answer and a different
request with the same key is refused.

```groovy
implementation libs.platform.idempotency
```

The module's changelog sets the table's place, then includes the platform's:

```yaml
databaseChangeLog:
  - property: { name: idempotency.schema, value: card }
  - property: { name: idempotency.table, value: card_idempotency }
  - include: { file: db/changelog/platform-idempotency.yaml }
```

```java
var idempotency = new Idempotency("card", "card_idempotency", dataSource, transactionManager, json);
var result = idempotency.execute(clientId, key, requestHash, PaymentResult.class, () -> pay(request));
```

- Keys are the client's own: two clients may use the same key.
- The same key with a different request hash is `IDEMPOTENCY_KEY_REUSED` (422).
- Two concurrent requests with one key meet at the table's primary key; the loser tries once more
  and replays the winner's answer.
- The module purges old keys on its own schedule: `purgeBefore(Instant)`.

## Tests

`./gradlew -p platform :idempotency:check`: replays, a reused key, two clients with one key, failed work keeping no record, a race
on one key, and the purge, on a real Postgres.

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

// For void / 204 No Content commands (refunds, cancellations):
idempotency.run(clientId, key, requestHash, () -> cancel(request));
```

- Keys are the client's own: two clients may use the same key.
- The key is claimed, and committed, before the work runs; the answer is recorded after it. A
  concurrent request with the same key gets `IDEMPOTENCY_IN_PROGRESS` (409) and never runs the
  work: safe for work that calls outside systems (a debit), not only for database writes.
- The same key with a different request hash is `IDEMPOTENCY_KEY_REUSED` (422).
- The work commits on its own. A `GeneralException` from it frees the key, so the client may
  correct the request and retry; anything else (no answer in time, a crash) leaves the outcome
  unknown, and the key stays in progress: the work is never run twice.
- With `keep`, a key older than that is free again; the module also purges old keys on its own
  schedule: `purgeBefore(Instant)`.
- With `inProgressTimeout`, orphaned in-progress keys left by worker node crashes mid-transaction
  (`response IS NULL`) are safely reclaimed after the timeout expires, preventing permanent key lockout.
- Configurable `Clock clock` enables deterministic time-travel testing.

## Tests

`./gradlew -p platform :idempotency:check`: replays, a reused key, two clients with one key, a refusal freeing the key, work without an
answer never run again, ten concurrent requests running the work once, `keep`, and the purge, on a
real Postgres.

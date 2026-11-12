# outbox

A transactional outbox for any module: an event is written to a table in the module's own schema,
in the module's own transaction, so a change and its event commit together or not at all. A relay
publishes the events to Kafka at least once, each key's events in the order written.

```groovy
implementation libs.platform.outbox
```

The module's changelog sets the table's place, then includes the platform's:

```yaml
databaseChangeLog:
  - property: { name: outbox.schema, value: card }
  - property: { name: outbox.table, value: card_outbox }
  - include: { file: db/changelog/platform-outbox.yaml }
```

The module builds one `Outbox` per table, on its own pool and transaction manager, and calls
`relay()` on its own schedule in the apps that should publish:

```java
var outbox = new Outbox("card", "card_outbox", dataSource, transactionManager, kafka, 100, tracer, propagator);
outbox.append(new OutboxEvent(UUID.randomUUID(), "card-events", cardId, "card.bound", payload));
```

- `append` joins the caller's transaction; without one it commits on its own.
- A row is marked published only after Kafka has it. A crash in between publishes it again, so a
  consumer drops repeats by the event id (the `event-id` header; `event-type` carries the type).
- One relay publishes at a time per outbox, across all replicas (a Postgres advisory lock): two
  at once would publish one key's events out of order. Another replica's relay simply finds
  nothing to do that round.
- Each event is sent inside the trace of the request that wrote it.

## Tests

`./gradlew -p platform :outbox:integrationTest`, on a real Postgres and Kafka: an event commits with
its transaction or not at all, one key's events arrive in order and once, an event Kafka never took
stays unpublished, and a relay waits while another replica's relay holds the outbox.

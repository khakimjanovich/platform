# persistence

What every module needs to own its data: its own connection pool, migrations, entity manager and
transactions, connecting as its own database user, which sees only its own schema. Liquibase keeps
its bookkeeping tables inside that schema, and Hibernate validates the tables and never changes
them. A module can move out to its own service unchanged.

```groovy
implementation libs.platform.persistence
```

Each module declares its persistence in one class:

```java
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableModulePersistence(
    schema = "card",
    prefix = "app.db.card",
    changelog = "db/changelog/card.yaml",
    entities = Card.class)
@EnableJpaRepositories(
    basePackageClasses = CardRepository.class,
    entityManagerFactoryRef = "cardEntityManagerFactory",
    transactionManagerRef = "cardTransactionManager")
public class CardPersistenceConfig {}
```

It registers `cardDataSource`, `cardLiquibase`, `cardEntityManagerFactory` and
`cardTransactionManager` (the schema + `PersistenceConstants.DATA_SOURCE`, …).
`@EnableTransactionManagement` stays on the class: as a meta-annotation it does not turn on
`@Transactional`. A module that needs to wrap one of them (e.g. its pool) declares the beans itself
with `ModulePersistence.dataSource / liquibase / entityManagerFactory / transactionManager`.

## Properties

| Property | Default | What |
|---|---|---|
| `app.db.<module>.url`, `.username`, `.password` | — | the module's connection (Boot's `DataSourceProperties`); the password from Vault |
| `app.db.<module>.hikari.*` | Hikari's | every Hikari setting, e.g. `maximum-pool-size`, set per environment in the local stack |
| `platform.persistence.jpa.properties` | — | Hibernate properties for every module, on top of the defaults (`hibernate.jdbc.time_zone=UTC`, `hibernate.jdbc.batch_size=20`, ordered inserts and updates) |
| `platform.persistence.liquibase.enabled` | true | false where migrations run apart from the app |

`spring.jpa.open-in-view` is false. A pool whose `keepalive-time` isn't shorter than its
`max-lifetime` stops startup.

## Errors

None of its own; a schema that doesn't match its entities stops startup.

## Tests

- `./gradlew -p platform :persistence:test`: the pool, the keepalive check, Hibernate's settings and
  the app-wide overrides.
- `./gradlew -p platform :persistence:integrationTest`: two modules on a real Postgres: migrations in
  their own schemas, no reading another's, pool settings, batches, UTC, the migration switch, and
  an entity ahead of its migrations stopping startup.

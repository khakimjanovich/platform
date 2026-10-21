# persistence

What every module needs to own its data: its own connection pool, migrations, entity manager and
transactions, connecting as its own database user, which sees only its own schema. Liquibase keeps
its bookkeeping tables inside that schema, and Hibernate validates the tables and never changes
them. A module can move out to its own service unchanged.

```groovy
implementation libs.ecom.persistence
```

Each module declares its beans with `ModulePersistence` (see any `modules/*/config/*PersistenceConfig`):

```java
@Bean @ConfigurationProperties("ecom.db.card")
DataSourceProperties cardDataSourceProperties() { return new DataSourceProperties(); }

@Bean @ConfigurationProperties("ecom.db.card.hikari")
HikariDataSource cardDataSource(DataSourceProperties cardDataSourceProperties) {
  return ModulePersistence.dataSource("card", cardDataSourceProperties);
}
// + ModulePersistence.liquibase(...), entityManagerFactory(...), transactionManager(...)
```

## Properties

| Property | Default | What |
|---|---|---|
| `ecom.db.<module>.url`, `.username`, `.password` | — | the module's connection (Boot's `DataSourceProperties`); the password from Vault |
| `ecom.db.<module>.hikari.*` | Hikari's | every Hikari setting, e.g. `maximum-pool-size`, set per environment in ecom-infra |
| `ecom.persistence.jpa.properties` | — | Hibernate properties for every module, on top of the defaults (`hibernate.jdbc.time_zone=UTC`, `hibernate.jdbc.batch_size=20`, ordered inserts and updates) |
| `ecom.persistence.liquibase.enabled` | true | false where migrations run apart from the app |

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

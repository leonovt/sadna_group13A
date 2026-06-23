# Data Persistence

Everything about how and where the system stores its data.

---

## Is the database on an external server?

**It depends on which profile you run.**

| Profile | Database | Where it lives |
|---|---|---|
| *(none / default)* | H2 file | A file on the **same machine** as the app (`./data/sadna-db.mv.db`) |
| `local` | H2 in-memory | Only in RAM — wiped on every restart |
| `prod` | PostgreSQL | On a **remote server** (GCP Cloud SQL or any PostgreSQL host) — separate machine from the app, by design |
| `demo` | H2 file | Same as default, plus demo data seeded on startup |

In production the database is deliberately on a different host. The connection details are
never hard-coded — they come from environment variables.

---

## Profiles in detail

### Default profile (`application.yml`)

Activated when you run `mvn spring-boot:run` with no profile flag.

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/sadna-db;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
```

- Uses **H2 in file mode**. Data is written to `./data/sadna-db.mv.db` relative to your
  working directory and survives restarts.
- `AUTO_SERVER=TRUE` lets multiple JVM processes share the same file — useful if you
  run tests and the app simultaneously.
- `ddl-auto: update` means Hibernate compares the schema to the entity definitions on
  startup and applies any missing columns or tables automatically. It never drops existing data.
- The H2 web console is available at `http://localhost:8080/h2-console` so you can
  browse the tables live.

### `local` profile (`application-local.yml`)

Activated with `mvn spring-boot:run -Dspring-boot.run.profiles=local`.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:sadna;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
```

- Uses **H2 in-memory**. Zero install, zero leftover files.
- `DB_CLOSE_DELAY=-1` keeps the database alive for the full JVM lifetime (without it H2
  would destroy the database once the last connection closes).
- `ddl-auto: create-drop` means the schema is built fresh on every startup and
  completely dropped on shutdown. Nothing persists between runs.
- Use this when you want a clean slate every time, e.g. during active development.

### `prod` profile (`application-prod.yml`)

Activated with `mvn spring-boot:run -Dspring-boot.run.profiles=prod`.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:sadna}
    username: ${DB_USERNAME:sadna}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
```

- Connects to a **remote PostgreSQL** instance. The host, port, database name, and
  credentials all come from environment variables — nothing is committed to source control.
- `ddl-auto: validate` means Hibernate **does not touch the schema**. It only checks
  that the database schema matches the entity definitions and throws an error at startup
  if they don't match. You are responsible for keeping the schema in sync (e.g. via
  migration scripts).
- This profile also activates the real external gateways (`WsepPaymentGateway`,
  `ExternalTicketSupplier`). The stub gateways used in all other profiles are excluded.

#### Required environment variables (prod)

| Variable | Default | Meaning |
|---|---|---|
| `DB_HOST` | `localhost` | Hostname or IP of the PostgreSQL server |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `sadna` | Database name |
| `DB_USERNAME` | `sadna` | Database login username |
| `DB_PASSWORD` | *(none — required)* | Database login password |

Example startup (PowerShell):
```powershell
$env:DB_HOST="your-db-host"; $env:DB_PASSWORD="your-password"
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### `demo` profile (`application-demo.yml`)

Activated with `mvn spring-boot:run -Dspring-boot.run.profiles=demo`.

Inherits everything from the default profile (H2 file database). The only thing it
adds is enabling `DemoDataSeeder`, which populates users, companies, and events on
startup so you have something to click around with immediately.

Demo accounts (password for all: `pass123`):

| Username | Role |
|---|---|
| `alice` | Founder — SoundWave Entertainment |
| `bob` | Manager — SoundWave (events, reports) |
| `carol` | Manager — SoundWave (policies, discounts) |
| `frank` | Founder — Stellar Experiences |
| `eve` | Manager — Stellar (all permissions) |
| `dave` | Regular member / buyer |

---

## How domain objects are stored

The persistence layer uses a **JSON-blob pattern** rather than a fully normalized
relational schema. Each aggregate is serialized to a single JSON string and stored in
a `@Lob` text column. A few queryable scalar columns sit alongside it so the database
can filter without deserializing the blob.

### Example: Event table (`events`)

| Column | Type | Purpose |
|---|---|---|
| `id` | `VARCHAR` PK | Aggregate ID |
| `company_id` | `VARCHAR` | FK-style lookup (find events by company) |
| `category` | `VARCHAR` | Allows category-based filtering |
| `published` | `BOOLEAN` | Allows filtering to published events only |
| `title` | `VARCHAR` | Text search on title |
| `data` | `TEXT` (`@Lob`) | Full `Event` object serialized as JSON |

The `data` column holds the entire aggregate: `VenueMap`, all `Zone`s, all `Seat`s,
the `PurchasePolicy` tree, the `DiscountPolicy` tree, version counter — everything.

### Example: User table (`users`)

| Column | Type | Purpose |
|---|---|---|
| `id` | `VARCHAR` PK | Aggregate ID |
| `username` | `VARCHAR` UNIQUE | Username lookup without deserializing |
| `data` | `TEXT` (`@Lob`) | Full `User` object serialized as JSON |

### Why JSON blobs?

The domain model is kept completely free of JPA annotations (`@Entity`, `@Column`, etc.).
This preserves clean architecture — the domain layer has no infrastructure dependency.
The repository implementation does the translation:

```
Load:  JPA entity (row) → JSON string → Jackson deserialize → domain object
Save:  domain object → Jackson serialize → JSON string → JPA entity (row) → SQL INSERT/UPDATE
```

Querying within the blob is not needed because the queryable scalar columns cover all
the filtering the application actually does. For anything more complex (e.g. full-text
search), the service layer loads candidates and filters them in memory.

---

## Optimistic locking

Both `User` and `Event` can be updated by concurrent requests (two users reserving the
last seat, an admin updating a user while they log in, etc.).

Because the domain objects have no JPA `@Version` annotation, optimistic locking is
implemented manually in the repository:

1. The domain object carries an in-memory `version` counter that increments on every
   mutation.
2. When `save(entity)` is called, the repository loads the currently stored row and
   compares versions.
3. If the stored version is **≥** the incoming version, someone else has already written
   a newer state — a `OptimisticLockException` is thrown.
4. The application service catches this and returns a failure result to the user.

```java
// EventRepositoryImpl.save() — simplified
Optional<EventEntity> stored = jpa.findById(event.getId());
if (stored.isPresent()) {
    Event storedDomain = toDomain(stored.get());
    if (storedDomain.getVersion() >= event.getVersion()) {
        throw new OptimisticLockException("Concurrent write conflict for Event " + event.getId());
    }
}
jpa.save(toEntity(event));
```

---

## Connection health monitoring

The system handles a database going offline gracefully — no crash, no hang, automatic
recovery when the database comes back.

### How it works

```
Every 5 seconds (configurable):
DataSourceHealthProbe.isReachable()
  └── borrows a JDBC connection from the pool
  └── calls Connection.isValid(2 seconds timeout)
  └── returns true/false

DatabaseConnectionManager.monitorConnection()
  └── updates an AtomicBoolean (connected / disconnected)
  └── logs "entering degraded mode" or "connection restored"

On every repository call:
PersistenceAvailabilityInvocationHandler.invoke()
  └── calls DatabaseConnectionManager.verifyConnected()
  └── if disconnected → throws PersistenceUnavailableException immediately
  └── if connected → delegates to the real repository method
```

The proxy (`PersistenceAvailabilityInvocationHandler`) is installed automatically by
`RepositoryAvailabilityBeanPostProcessor`, a Spring `BeanPostProcessor` that wraps every
repository bean at startup. No repository class needs to be modified.

From the user's perspective: if the database goes down, actions that need persistence
return an error message ("Service temporarily unavailable — please retry shortly.").
Read-only operations that are already cached in memory may still work. Once the database
comes back the next health probe detects it and normal operation resumes automatically
without a restart.

### Configuring the probe interval

```yaml
app:
  persistence:
    health-check-interval-ms: 5000   # default: check every 5 seconds
```

### Classes involved

| Class | Role |
|---|---|
| `DataSourceHealthProbe` | Borrows a real JDBC connection and calls `isValid()`. `@Primary` — used in production. |
| `InMemoryDatabaseHealthProbe` | Manual-control stub used only in tests (lets a test simulate a DB outage). |
| `DatabaseHealthProbe` | Interface between the two above. |
| `DatabaseConnectionManager` | Holds the health flag. Runs the scheduled probe. Exposes `verifyConnected()`. |
| `PersistenceAvailabilityInvocationHandler` | JDK dynamic proxy — calls `verifyConnected()` before every repository method. |
| `RepositoryAvailabilityBeanPostProcessor` | Spring `BeanPostProcessor` — wraps all `*RepositoryImpl` beans in the proxy at startup. |

---

## Full config reference (persistence-related keys)

| Key | Profile | Default | Description |
|---|---|---|---|
| `spring.datasource.url` | all | H2 file URL | JDBC connection string |
| `spring.datasource.username` | all | `sa` / `sadna` | Database username |
| `spring.datasource.password` | all | `""` | Database password |
| `spring.datasource.driver-class-name` | all | `org.h2.Driver` | JDBC driver class |
| `spring.jpa.database-platform` | all | `H2Dialect` | Hibernate SQL dialect |
| `spring.jpa.hibernate.ddl-auto` | all | `update` | Schema management strategy (see below) |
| `spring.jpa.show-sql` | all | `false` | Print every SQL statement to stdout |
| `spring.jpa.open-in-view` | default | `false` | Disabled — keeps JPA sessions scoped to transactions only |
| `spring.h2.console.enabled` | default/local | `true` | Enables the H2 browser console at `/h2-console` |
| `spring.h2.console.path` | default | `/h2-console` | Path to the H2 console |
| `app.persistence.health-check-interval-ms` | all | `5000` | How often (ms) the DB health probe runs |
| `DB_HOST` *(env var)* | prod | `localhost` | PostgreSQL server hostname |
| `DB_PORT` *(env var)* | prod | `5432` | PostgreSQL port |
| `DB_NAME` *(env var)* | prod | `sadna` | PostgreSQL database name |
| `DB_USERNAME` *(env var)* | prod | `sadna` | PostgreSQL username |
| `DB_PASSWORD` *(env var)* | prod | *(required)* | PostgreSQL password |

### `ddl-auto` values explained

| Value | When used | What Hibernate does |
|---|---|---|
| `update` | Default (H2 file) | Adds missing tables/columns on startup. Never drops anything. Data survives restarts. |
| `create-drop` | `local` profile | Creates the full schema on startup, drops it on shutdown. Always starts clean. |
| `validate` | `prod` profile | Does nothing to the schema — only checks it matches the entities. Startup fails if they don't match. |

---

## H2 web console

When running with the default or `local` profile, you can inspect the database directly
in a browser:

- URL: `http://localhost:8080/h2-console`
- JDBC URL: paste the value from your active profile (e.g. `jdbc:h2:file:./data/sadna-db`)
- Username: `sa`
- Password: *(leave blank)*

This is useful for checking what rows were actually written, inspecting the JSON in the
`data` column, or debugging unexpected behaviour.

# sadna_group13A

Event management & ticketing platform (Ben-Gurion University — Software Engineering Workshop 2026).

## Running the system

```bash
mvn spring-boot:run
```

Profiles select the runtime configuration (database, seed data, etc.):

```bash
# Local development with an in-memory H2 database
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Remote PostgreSQL (production-like)
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# Demo data seeding (sample users/companies/events)
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

## System initialization

On startup the platform initializes itself (`PlatformBootstrap` → `SystemService.initializePlatform`):
it verifies the external payment/ticketing services are reachable and creates the **root admin**
account. The admin credentials come from `app.admin.*` in configuration:

| Profile | Username | Password |
|---------|----------|----------|
| default / `local` / `prod` | `admin` | `admin123` |
| `demo` | `yahlitheking` | `roee0` |

## Database configuration

Database connection details are **not hardcoded** in Java — they come entirely from the
external configuration files under `src/main/resources/`. Switching between a local and a
remote database requires **only a profile change** (no code change):

| Profile | File | Database | When to use |
|---------|------|----------|-------------|
| `local` | `application-local.yml` | In-memory **H2** | Development on a single machine |
| `prod`  | `application-prod.yml`  | Remote **PostgreSQL** (e.g. GCP) | Production / remote DB |

### Parameters

Defined under `spring.datasource.*` and `spring.jpa.*`:

| Parameter | Meaning |
|-----------|---------|
| `spring.datasource.url` | JDBC connection URL (host, port, database name) |
| `spring.datasource.username` | Database user |
| `spring.datasource.password` | Database password (**from an environment variable in `prod`**) |
| `spring.datasource.driver-class-name` | JDBC driver (`org.h2.Driver` / `org.postgresql.Driver`) |
| `spring.jpa.database-platform` | Hibernate dialect (`H2Dialect` / `PostgreSQLDialect`) |
| `spring.jpa.hibernate.ddl-auto` | Schema strategy (`create-drop` local, `validate` prod) |

### Secrets

The remote database password is **never committed**. In the `prod` profile it is read from the
`DB_PASSWORD` environment variable (along with `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`).
`.env` files are gitignored.

```powershell
# PowerShell example
$env:DB_HOST = "your-gcp-host"
$env:DB_PASSWORD = "your-password"
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

> Note: the ORM/persistence layer (JPA starter + JDBC drivers) is delivered separately. Until it
> is on the classpath, Spring ignores `spring.datasource.*` / `spring.jpa.*`; this configuration is
> ready so that enabling persistence becomes a config-only switch.

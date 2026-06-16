# sadna_group13A — Event Ticketing System

A Spring Boot 3 event-ticketing platform that supports regular sales, virtual queues,
and lottery-based (raffle) seat allocation.  
Built for the Software Engineering Workshop (Sadna) course — Group 13A.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17 |
| Maven | 3.8+ |

---

## Quick Start (local — H2 in-memory database)

```bash
# 1. Clone the repo
git clone https://github.com/leonovt/sadna_group13A.git
cd sadna_group13A

# 2. Run with the default profile (H2 in-memory, no external services required)
mvn spring-boot:run

# 3. Open the UI
#    http://localhost:8080
#    Bootstrap admin: admin / admin123  (see app.admin.* below)
```

To seed demo data (companies, events, users) automatically on startup:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Demo accounts (password for all: `pass123`):

| Username | Role |
|----------|------|
| alice | Founder — SoundWave Entertainment |
| bob | Manager — SoundWave (events, reports) |
| carol | Manager — SoundWave (policies, discounts) |
| frank | Founder — Stellar Experiences |
| eve | Manager — Stellar (all permissions) |
| dave | Regular buyer |

---

## Running against PostgreSQL (production / GCP)

1. Create a PostgreSQL database and note the connection details.

2. Create `src/main/resources/application-prod.yml` (never commit credentials):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://<host>:<port>/<dbname>
    username: <db-user>
    password: <db-password>
  jpa:
    hibernate:
      ddl-auto: validate          # schema managed by Flyway / migration script
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

external:
  system:
    url: https://damp-lynna-wsep-1984852e.koyeb.app/

app:
  admin:
    username: <your-admin-username>
    password: <your-admin-password>
```

3. Start with the `prod` profile:

```bash
java -jar target/sadna-group13a-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

Or via Maven:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

> **Note:** The `prod` profile activates the real payment and ticket HTTP clients.
> Make sure the external system is reachable before starting — the application
> performs a handshake on startup and refuses to initialize if either gateway
> is unreachable.

---

## Configuration Reference

All keys are defined in `src/main/resources/application.yml`.
Override any key in a profile-specific file (`application-prod.yml`,
`application-test.yml`, etc.) or via environment variable
(e.g. `SPRING_DATASOURCE_URL`).

### Application keys

| Key | Default | Description |
|-----|---------|-------------|
| `app.admin.username` | `admin` | Username of the bootstrap administrator account created on first startup. |
| `app.admin.password` | `admin123` | Password for the bootstrap administrator. **Change before production.** |
| `app.seat.hold-duration-minutes` | `10` | How long a seat is reserved (HELD) in a user's cart before it is automatically released. |
| `app.jwt.secret-key` | *(see yml)* | HMAC-SHA256 secret used to sign JWT tokens. Must be ≥ 256 bits (32 chars). **Rotate before production.** |
| `app.jwt.expiration-ms` | `3600000` | JWT lifetime in milliseconds. Default: 1 hour. |

### Database keys (V3 — required when using PostgreSQL)

| Key | Example | Description |
|-----|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/ticketing` | JDBC URL of the database. Use `jdbc:h2:mem:testdb` for local H2. |
| `spring.datasource.username` | `ticketing_user` | Database login username. |
| `spring.datasource.password` | `s3cr3t` | Database login password. |
| `spring.jpa.hibernate.ddl-auto` | `create-drop` (H2) / `validate` (prod) | Schema management strategy. Use `create-drop` for local dev and `validate` for production (schema managed by migration scripts). |

### External system keys (V3 — required for payment and ticket services)

| Key | Default | Description |
|-----|---------|-------------|
| `app.external.payment.mode` | `stub` | `stub` (in-memory, tests/local) or `wsep` (real external payment service). |
| `app.external.payment.url` | `https://damp-lynna-wsep-1984852e.koyeb.app/` | Base URL of the external payment API (used only when `mode=wsep`). |
| `app.ticketing.mode` | `stub` | `stub` (in-memory, tests/local) or `wsep` (real external ticket-issuance service). |
| `app.ticketing.url` | `https://damp-lynna-wsep-1984852e.koyeb.app/` | Base URL of the external ticket-issuance API (used only when `mode=wsep`). |
| `app.external.connect-timeout-ms` | `5000` | TCP connection timeout in milliseconds, shared by every call to the external payment and ticket-issuance system (issue #241) — never hangs indefinitely on an unresponsive service. |
| `app.external.read-timeout-ms` | `10000` | Socket read timeout in milliseconds while waiting for the external system's response, shared by both external call sites. |

### Minimal `application.yml` for local development (H2)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:ticketingdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop

app:
  admin:
    username: admin
    password: admin123
  seat:
    hold-duration-minutes: 10
  jwt:
    secret-key: change-me-to-at-least-256-bits-long-for-hs256-algorithm
    expiration-ms: 3600000
```

---

## Initial-State File Format

The system supports loading an initial state from a plain-text file on startup
(implemented as part of issue [#224](https://github.com/leonovt/sadna_group13A/issues/224)).
This lets you define users, companies, and events declaratively
without the demo data seeder.

### Syntax rules

- One operation per line.
- Lines starting with `#` are comments and are ignored.
- Blank lines are ignored.
- Format: `operation-name(arg1, arg2, ...)`
- Arguments are trimmed of leading/trailing whitespace.
- `login` stores the returned JWT as a named variable `<username>_token`
  which can be referenced in later lines.
- `open-production-company` stores the returned company ID as
  `<company-name>_id` (spaces replaced with underscores, lower-cased).

### Supported operations

| Operation | Arguments | Description |
|-----------|-----------|-------------|
| `guest-registration` | `username, password` | Registers a new member account. |
| `login` | `username, password` | Authenticates the user; stores the JWT as `{username}_token`. |
| `open-production-company` | `{username}_token, name, description` | Creates a production company owned by the authenticated user. Stores the new ID as `{name}_id`. |
| `appoint-manager` | `{username}_token, company_id, target_username, PERMISSION...` | Appoints a manager; one or more permissions from `MANAGE_EVENTS`, `MANAGE_POLICIES`, `MANAGE_DISCOUNTS`, `VIEW_REPORTS`. |
| `accept-nomination` | `{username}_token, company_id` | The nominated user accepts a management appointment. |

> The loader is implemented in `#224`. Until that issue is merged, use the
> `demo` Spring profile or the `DemoDataSeeder` for programmatic seeding.

### Example file

See [`init-state-example.txt`](./init-state-example.txt) at the repo root for a
runnable example. Abbreviated version:

```
# ── Users ────────────────────────────────────────────────────────────────────
guest-registration(alice, securePass1)
guest-registration(bob,   securePass2)

# ── Authentication ───────────────────────────────────────────────────────────
login(alice, securePass1)
# alice_token is now available for subsequent calls

# ── Company ──────────────────────────────────────────────────────────────────
open-production-company(alice_token, SoundWave, Live music events company)
# soundwave_id is now available

# ── Staff appointment ────────────────────────────────────────────────────────
login(bob, securePass2)
appoint-manager(alice_token, soundwave_id, bob, MANAGE_EVENTS, VIEW_REPORTS)
accept-nomination(bob_token, soundwave_id)
```

### Activating the initial-state file

Point the application to the file via `application.yml`:

```yaml
app:
  init:
    state-file: classpath:initial-state.txt   # or an absolute file path
```

Or pass it at runtime:

```bash
java -jar sadna-group13a.jar --app.init.state-file=/etc/ticketing/initial-state.txt
```

---

## Build & Test

```bash
# Build (skips tests)
mvn package -DskipTests

# Run all tests
mvn test

# Run with coverage report (output: target/site/jacoco/index.html)
mvn verify
```

Minimum coverage enforced by JaCoCo: **75% line coverage**
(presentation layer and `DemoDataSeeder` are excluded).

---

## Project Structure

```
src/
├── main/
│   ├── java/com/sadna/group13a/
│   │   ├── application/          # Services, DTOs, interfaces
│   │   ├── domain/               # Aggregates, domain services, policies
│   │   ├── infrastructure/       # Repository implementations, gateways, bootstrap
│   │   └── presentation/         # Vaadin UI views
│   └── resources/
│       ├── application.yml       # Default configuration (H2 file DB, stub gateways)
│       ├── application-local.yml # Local profile (in-memory H2)
│       ├── application-prod.yml  # Prod profile (remote PostgreSQL + real WSEP gateways)
│       └── application-demo.yml  # Demo-profile overrides
└── test/
    └── java/com/sadna/group13a/  # Unit, integration, and acceptance tests
```

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.2.5 |
| Language | Java 17 |
| UI | Vaadin 24.3.13 |
| Auth | JJWT 0.12.5 (HS256) |
| Persistence (V3) | Spring Data JPA / Hibernate |
| DB — local | H2 (in-memory) |
| DB — production | PostgreSQL (GCP Cloud SQL) |
| Testing | JUnit 5, Mockito, JaCoCo |

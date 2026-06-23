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
mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
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

## Running against PostgreSQL (remote DB — SL-7)

`src/main/resources/application-prod.yml` already ships with the `prod` profile. It reads
the **remote** database connection from **environment variables** (so no secret is committed)
and sets the PostgreSQL dialect with `ddl-auto: validate`. Switching H2 ↔ PostgreSQL is a
**config/profile change only — no code change.**

1. Create a PostgreSQL database (it must be on a different host than the app — remote-DB
   requirement) and note its connection details.

2. Export the connection env vars (defaults shown after the colon; `DB_PASSWORD` has none):

   | Env var | Default | Meaning |
   |---------|---------|---------|
   | `DB_HOST` | `localhost` | database host |
   | `DB_PORT` | `5432` | database port |
   | `DB_NAME` | `sadna` | database name |
   | `DB_USERNAME` | `sadna` | database user |
   | `DB_PASSWORD` | *(required)* | database password |

3. Start with the `prod` profile:

```bash
# PowerShell
$env:DB_HOST="<host>"; $env:DB_PASSWORD="<password>"; mvn spring-boot:run -Dspring-boot.run.profiles=prod

# bash
DB_HOST=<host> DB_PORT=5432 DB_NAME=sadna DB_USERNAME=<user> DB_PASSWORD=<password> \
  mvn spring-boot:run -Dspring-boot.run.profiles=prod


docker run -e POSTGRES_DB=sadna -e POSTGRES_USER=sadna -e POSTGRES_PASSWORD=pw -p 5433:5432 postgres:16


$env:DB_HOST="localhost"; $env:DB_PORT="5433"; $env:DB_NAME="sadna"; $env:DB_USERNAME="sadna"; $env:DB_PASSWORD="pw"; $env:SPRING_JPA_HIBERNATE_DDL_AUTO="update"; mvn spring-boot:run "-Dspring-boot.run.profiles=prod"

Database available at 'jdbc:postgresql://localhost:5433/sadna'


```

> **Note:** Activating the `prod` profile is what selects the **real** external gateways
> (`WsepPaymentGateway` + `ExternalTicketSupplier`, both `@Profile("prod")`); in every other
> profile the stub gateways (`@Profile("!prod")`) are used. Selection is **profile-driven**,
> not property-driven. Make the external system reachable before starting.
>
> A free **local Docker PostgreSQL** is the cheapest way to prove the remote-DB path without
> a cloud account, e.g.:
> `docker run -e POSTGRES_DB=sadna -e POSTGRES_USER=sadna -e POSTGRES_PASSWORD=pw -p 5433:5432 postgres:16`

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

### External system keys (V3 — payment and ticket services)

> **Gateway selection is profile-driven, not property-driven.** The real
> `WsepPaymentGateway` and `ExternalTicketSupplier` are `@Profile("prod")`; the stub
> gateways are `@Profile("!prod")`. There is **no** `mode` switch — activate `prod` to use
> the real WSEP systems. The URLs below are only consulted by the real (prod) gateways.

| Key | Default | Description |
|-----|---------|-------------|
| `app.external.payment.url` | `https://damp-lynna-wsep-1984852e.koyeb.app/` | Base URL of the external WSEP payment API (used by the prod-profile gateway). |
| `app.ticketing.url` | `https://damp-lynna-wsep-1984852e.koyeb.app/` | Base URL of the external WSEP ticket-issuance API (used by the prod-profile gateway). |
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

## Initialization (config + initial-state file)

Startup has two independent stages (requirement **I.1**):

1. **Config-file init.** `app.init.*` in `application.yml` (or any active profile / external
   config) is bound to `SystemInitProperties` and **validated** — an invalid/missing value
   makes the context fail to start (e.g. `max-concurrent-users-per-event` must be ≥ 1). DB
   connection details come from config/env only, never hard-coded. The system **fails to
   start** on invalid config.
2. **Initial-state file (optional).** After the root admin is bootstrapped, an optional file
   replays a **series of use-case stories** through the application layer so the system reaches
   a defined state. It is **all-or-nothing**: the first failed or illegal operation aborts the
   whole startup with a clear error.

### Initial-state file — canonical JSON format (`app.init.initial-state-file`)

The canonical loader is JSON: a list of operations, each `{ "action", "args", "bindTo" }`.
`bindTo` names the value an operation returns (a JWT or an id) so later operations can reference
it; any `args` value equal to a previously bound name resolves to that value.

```json
[
  { "action": "register", "args": { "username": "alice", "password": "password123" } },
  { "action": "login",    "args": { "username": "alice", "password": "password123" }, "bindTo": "alice_token" },
  { "action": "create-company",
    "args": { "token": "alice_token", "name": "SoundWave", "description": "Live music" },
    "bindTo": "soundwave" },
  { "action": "register", "args": { "username": "bob", "password": "password123" } },
  { "action": "login",    "args": { "username": "bob", "password": "password123" }, "bindTo": "bob_token" },
  { "action": "appoint-manager",
    "args": { "token": "alice_token", "companyId": "soundwave", "targetUsername": "bob",
              "permissions": "MANAGE_EVENTS, VIEW_REPORTS" } },
  { "action": "accept-nomination", "args": { "token": "bob_token", "companyId": "soundwave" } },
  { "action": "create-event",
    "args": { "token": "alice_token", "companyId": "soundwave", "title": "Jazz Night",
              "date": "2026-08-01T20:00" }, "bindTo": "jazz" },
  { "action": "publish-event", "args": { "token": "alice_token", "eventId": "jazz" } }
]
```

**Supported actions** (see `infrastructure/initstate/InitialStateExecutor`): `register`,
`login`, `logout`, `enter-as-guest`, `create-company`, `appoint-owner`, `appoint-manager`
(`permissions` = comma-separated `MANAGE_EVENTS,MANAGE_POLICIES,MANAGE_DISCOUNTS,VIEW_REPORTS`),
`accept-nomination`, `reject-nomination`, `suspend-company`, `reopen-company`, `create-event`,
`publish-event`, `set-sale-mode` (`REGULAR|QUEUE|RAFFLE`), `add-to-cart`
(`seatId` or `quantity`, binds the order id), `checkout` (`orderId`, optional `authCode`,
`paymentDetails`), `create-venue-map` (assigns zones to an event — see below), and
`set-company-coupon-discount` (sets a coupon discount policy on a company).

For a full reference of every action and its arguments, see [`CONFIG_SCHEMA.md`](./CONFIG_SCHEMA.md).

### Running with a config file — step by step

The repo ships with a ready-made example at [`config-example.json`](./config-example.json).
It sets up four users, a production company, an owner, a manager, an event with a standing
zone and a seated zone, and a coupon discount — all without touching the UI.

**Step 1.** Open `src/main/resources/application.yml` and set the file path:

```yaml
app:
  init:
    initial-state-file: config-example.json
```

The path is resolved relative to the working directory (i.e. the project root when you run
`mvn spring-boot:run`). You can also use an absolute path or a `classpath:` prefix.

**Step 2.** Start the application normally:

```bash
mvn spring-boot:run
```

Watch the startup log — you should see:

```
Initial-state loaded: 19 operation(s) executed successfully.
```

If any operation fails the application refuses to start and prints which operation failed and why.

**Step 3.** Open `http://localhost:8080` and log in with one of the seeded accounts:

| Username | Password   | Role in company p1 |
|----------|------------|--------------------|
| u1       | u1pass123  | Founder |
| u2       | u2pass123  | Owner |
| u3       | u3pass123  | Manager (MANAGE_EVENTS only) |
| u4       | u4pass123  | Regular member (no company role) |

You can also pass the path at runtime without editing `application.yml`:

```bash
# bash
mvn spring-boot:run -Dspring-boot.run.arguments="--app.init.initial-state-file=config-example.json"

# PowerShell
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.init.initial-state-file=config-example.json"

# or as an env var
APP_INIT_INITIALSTATEFILE=config-example.json mvn spring-boot:run
```

To use your own config file, copy `config-example.json`, modify it, and point the property at your copy. See [`CONFIG_SCHEMA.md`](./CONFIG_SCHEMA.md) for the full action reference and the `create-venue-map` / `set-company-coupon-discount` syntax.

A minimal runnable example also lives at [`src/main/resources/init-state.sample.json`](./src/main/resources/init-state.sample.json):

```yaml
app:
  init:
    initial-state-file: classpath:init-state.sample.json
```

### Legacy text loader (`app.init.state-file`)

A second, **simpler** loader accepts a whitespace-delimited text file — one command per line
from `register` / `login` / `logout` / `create-company` (double quotes group multi-word
arguments). It is kept for quick local seeding only; prefer the JSON loader above for anything
non-trivial. See [`init-state-example.txt`](./init-state-example.txt) at the repo root.

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

# sadna_group13A

## Startup configuration

System startup parameters are driven by an **external configuration file** and are **not**
hardcoded. They live under `app.init.*` in `src/main/resources/application.yml` (or any active
profile / external config file) and are bound to `SystemInitProperties`.

The configuration is **validated on startup**: if a required value is missing or invalid, the
application **refuses to start** (fail-fast) rather than running in a misconfigured state.

| Parameter | Required | Meaning |
|-----------|----------|---------|
| `app.init.max-concurrent-users-per-event` | yes (≥ 1) | Max users who may hold tickets for a single event at once before additional visitors are routed to the virtual queue. Used as the default queue capacity when an owner enables QUEUE mode without specifying one. |
| `app.init.initial-state-file` | no | Path to an initial-state JSON file executed at startup (see issue #224). Leave blank to skip. |

Database connection parameters also come from configuration (no hardcoded credentials) — see the
"Database configuration" section (issue #222).

### Example

```yaml
app:
  init:
    max-concurrent-users-per-event: 100
    initial-state-file:
```

To override at runtime without editing files:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.init.max-concurrent-users-per-event=250
```

## Initial-state file

Optionally, the system can be brought to a known state at startup by replaying a sequence of
use-cases from a **separate** JSON file (distinct from the configuration file). Point
`app.init.initial-state-file` at it:

```yaml
app:
  init:
    initial-state-file: classpath:init-state.sample.json   # or a filesystem path
```

The path is resolved as a filesystem path (absolute, or relative to the working directory); if not
found there it is looked up on the classpath. A `classpath:` prefix forces a classpath lookup. A
ready-to-use example lives at `src/main/resources/init-state.sample.json`.

### Format

The file is a JSON **array of operations**, executed in order. Each operation has:

| Field | Required | Meaning |
|-------|----------|---------|
| `action` | yes | The use-case to invoke (see the table below). |
| `args` | depends | Named arguments for the use-case. |
| `bindTo` | no | A name bound to the value this operation returns (a token or an entity id), for reuse by later operations. |

Any `args` value equal to a previously bound name is **resolved to that bound value** — this is how
a token returned by `login` is passed to later operations.

All operations go through the **application/service layer** only. Initialization is
**all-or-nothing**: if any operation fails, the error is reported and the application **does not
start**.

### Supported actions

| `action` | `args` | Binds (`bindTo`) |
|----------|--------|------------------|
| `register` | `username`, `password` | — |
| `login` | `username`, `password` | auth token |
| `enter-as-guest` | — | guest token |
| `create-company` | `token`, `name`, `description?` | company id |
| `create-event` | `token`, `companyId`, `title`, `description?`, `date` (ISO `yyyy-MM-ddThh:mm`), `category?`, `artist?`, `location?` | event id |
| `publish-event` | `token`, `eventId` | — |

### Example

```json
[
  { "action": "register", "args": { "username": "alice", "password": "password123" } },
  { "action": "login",    "args": { "username": "alice", "password": "password123" }, "bindTo": "alice_token" },
  { "action": "create-company",
    "args": { "token": "alice_token", "name": "SoundWave Entertainment", "description": "Live music promoter" },
    "bindTo": "soundwave" },
  { "action": "create-event",
    "args": { "token": "alice_token", "companyId": "soundwave", "title": "Jazz Night 2026",
              "date": "2026-08-01T20:00", "category": "Live Music", "artist": "The Quartet", "location": "Tel Aviv" },
    "bindTo": "jazz_night" },
  { "action": "publish-event", "args": { "token": "alice_token", "eventId": "jazz_night" } }
]
```

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

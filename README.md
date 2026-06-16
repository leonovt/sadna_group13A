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

## Payment integration

The system charges and refunds through an external payment service. Which implementation is used is
selected by configuration — the URL is **never hardcoded**:

```yaml
app:
  external:
    payment:
      mode: stub   # 'stub' (in-memory, default — used by tests and local dev) or 'wsep'
      url: https://damp-lynna-wsep-1984852e.koyeb.app/   # used only when mode=wsep
```

| Parameter | Meaning |
|-----------|---------|
| `app.external.payment.mode` | `stub` (default `StubPaymentGateway`) or `wsep` (real `WsepPaymentGateway`). |
| `app.external.payment.url`  | WSEP endpoint; used only when `mode=wsep`. |

When `mode=wsep`:
- At startup the platform performs a `handshake` to verify the service is reachable (init fails otherwise).
- Checkout collects the card fields (number, holder, expiry, CVV, ID, currency) and calls `pay`; the
  returned transaction id is stored on the order receipt.
- On cancellation or any post-payment failure the system calls `refund` for the **entire** transaction
  (WSEP supports only full refunds).
- Timeouts, non-2xx responses, malformed bodies and `-1` results are handled gracefully (no crash).

Tests always run with `mode=stub`, so they never contact the real WSEP service.

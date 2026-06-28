# Configuration File Schema

The system can be initialized from a JSON configuration file at startup. This lets you seed the database with users, companies, events, zones, and policies without manual setup — useful for testing and demos.

## How to Enable

Set the path in `src/main/resources/application.yml`:

```yaml
app:
  init:
    initial-state-file: config-example.json
```

The path is resolved first as a filesystem path (absolute, or relative to the working directory). If not found there, it falls back to the classpath. Use a `classpath:` prefix to force a classpath lookup:

```yaml
    initial-state-file: classpath:my-init-state.json
```

You can also override via environment variable at runtime:

```
APP_INIT_INITIALSTATEFILE=config-example.json
```

---

## File Format

The file is a **JSON array** of operation objects. Operations are executed in order. Execution is **all-or-nothing**: if any operation fails the application refuses to start and logs the error.

```json
[
  { "action": "...", "args": { ... }, "bindTo": "..." },
  ...
]
```

### Operation object fields

| Field    | Type   | Required | Description |
|----------|--------|----------|-------------|
| `action` | string | yes      | The use-case to execute (see table below) |
| `args`   | object | yes      | Named arguments for the action |
| `bindTo` | string | no       | Name to bind the returned value (token, id) for reuse in later operations |

---

## Binding Mechanism

When an operation includes `"bindTo": "some_name"`, the value it returns (a JWT token, a company ID, an event ID, etc.) is stored under that name. Any later operation that uses `"some_name"` as an argument value will have it automatically replaced with the actual stored value.

Example:

```json
[
  { "action": "login",
    "args": { "username": "alice", "password": "secret" },
    "bindTo": "alice_token" },

  { "action": "create-company",
    "args": { "token": "alice_token", "name": "Acme" },
    "bindTo": "acme_id" },

  { "action": "create-event",
    "args": { "token": "alice_token", "companyId": "acme_id", "title": "Concert", "date": "2026-10-01T19:00" } }
]
```

Here `"alice_token"` and `"acme_id"` are symbolic names resolved at runtime — they never appear literally in the system.

---

## Supported Actions

### `register`
Creates a new member account.

| Arg        | Required | Description |
|------------|----------|-------------|
| `username` | yes      | Unique username |
| `password` | yes      | Plain-text password |

```json
{ "action": "register", "args": { "username": "u1", "password": "u1pass123" } }
```

---

### `login`
Authenticates a user and returns a JWT token.

| Arg        | Required | Description |
|------------|----------|-------------|
| `username` | yes      | Registered username |
| `password` | yes      | User's password |

`bindTo` → stores the JWT token.

```json
{ "action": "login", "args": { "username": "u1", "password": "u1pass123" }, "bindTo": "u1_token" }
```

---

### `logout`
Invalidates an active token.

| Arg     | Required | Description |
|---------|----------|-------------|
| `token` | yes      | Active JWT token (or bound name) |

```json
{ "action": "logout", "args": { "token": "u1_token" } }
```

---

### `enter-as-guest`
Creates an anonymous guest session.

No arguments required.

`bindTo` → stores the guest token.

```json
{ "action": "enter-as-guest", "bindTo": "guest_token" }
```

---

### `create-company`
Creates a new production company. The caller becomes its Founder.

| Arg           | Required | Description |
|---------------|----------|-------------|
| `token`       | yes      | Authenticated user token |
| `name`        | yes      | Company name (must be unique) |
| `description` | no       | Optional description |

`bindTo` → stores the company ID.

```json
{ "action": "create-company",
  "args": { "token": "u1_token", "name": "Acme Events", "description": "Live concerts" },
  "bindTo": "acme_id" }
```

---

### `appoint-owner`
Nominates a user as Owner of a company. The nominee must accept via `accept-nomination`.

| Arg              | Required | Description |
|------------------|----------|-------------|
| `token`          | yes      | Founder or Owner token |
| `companyId`      | yes      | Company ID (or bound name) |
| `targetUsername` | yes      | Username of the nominee |

```json
{ "action": "appoint-owner",
  "args": { "token": "u1_token", "companyId": "acme_id", "targetUsername": "u2" } }
```

---

### `appoint-manager`
Nominates a user as Manager with specific permissions. The nominee must accept via `accept-nomination`.

| Arg              | Required | Description |
|------------------|----------|-------------|
| `token`          | yes      | Founder or Owner token |
| `companyId`      | yes      | Company ID (or bound name) |
| `targetUsername` | yes      | Username of the nominee |
| `permissions`    | no       | Comma-separated list of permissions (see below) |

Available permissions: `MANAGE_EVENTS`, `MANAGE_POLICIES`, `MANAGE_DISCOUNTS`, `VIEW_REPORTS`

```json
{ "action": "appoint-manager",
  "args": {
    "token": "u1_token",
    "companyId": "acme_id",
    "targetUsername": "u3",
    "permissions": "MANAGE_EVENTS,VIEW_REPORTS"
  }
}
```

---

### `accept-nomination`
Confirms a pending Owner or Manager nomination.

| Arg         | Required | Description |
|-------------|----------|-------------|
| `token`     | yes      | Nominee's token |
| `companyId` | yes      | Company ID (or bound name) |

```json
{ "action": "accept-nomination", "args": { "token": "u2_token", "companyId": "acme_id" } }
```

---

### `reject-nomination`
Declines a pending nomination.

| Arg         | Required | Description |
|-------------|----------|-------------|
| `token`     | yes      | Nominee's token |
| `companyId` | yes      | Company ID (or bound name) |

```json
{ "action": "reject-nomination", "args": { "token": "u2_token", "companyId": "acme_id" } }
```

---

### `create-event`
Creates an event under a company. Caller must have `MANAGE_EVENTS` permission (Founders and Owners have it implicitly).

| Arg           | Required | Description |
|---------------|----------|-------------|
| `token`       | yes      | Authorized user token |
| `companyId`   | yes      | Company ID (or bound name) |
| `title`       | yes      | Event title |
| `date`        | yes      | ISO 8601 date-time, e.g. `2026-08-01T20:00` |
| `description` | no       | Event description |
| `category`    | no       | Category label |
| `artist`      | no       | Performing artist |
| `location`    | no       | Venue location |

`bindTo` → stores the event ID.

```json
{ "action": "create-event",
  "args": {
    "token": "u1_token",
    "companyId": "acme_id",
    "title": "Jazz Night 2026",
    "date": "2026-08-01T20:00",
    "category": "Live Music",
    "artist": "The Quartet",
    "location": "Tel Aviv"
  },
  "bindTo": "jazz_id" }
```

---

### `create-venue-map`
Assigns a venue map with one or more zones to an event. Each zone is either standing (capacity-based) or seated (individual numbered seats).

| Arg         | Required | Description |
|-------------|----------|-------------|
| `token`     | yes      | Authorized user token |
| `eventId`   | yes      | Event ID (or bound name) |
| `venueName` | no       | Display name for the venue (default: `"Main Venue"`) |
| `zones`     | yes      | JSON array of zone specification objects (see below) |

**Zone object fields:**

| Field       | Required | Description |
|-------------|----------|-------------|
| `name`      | yes      | Zone display name |
| `type`      | yes      | `STANDING` or `SEATED` |
| `basePrice` | yes      | Base ticket price for this zone |
| `rows`      | SEATED   | Number of seat rows (SEATED zones — see note) |
| `columns`   | SEATED   | Number of seats per row (SEATED zones — see note) |
| `capacity`  | STANDING | Maximum standing spots (STANDING zones) |

**SEATED zones use `rows` and `columns`.** This matches the venue-map display, which renders
a seat grid: the system generates `rows × columns` individual seats labelled by row letter and
column number — `A1 … A{columns}`, `B1 …`, etc. (rows beyond `Z` fall back to `R27`, `R28`, …).
A `10 × 10` seated zone therefore produces 100 seats `A1 … J10`.

> Back-compatibility: a SEATED zone may instead give a flat `capacity` (no `rows`/`columns`),
> which generates `capacity` seats labelled `"{name} 1" … "{name} N"`. Prefer `rows`/`columns`
> so the seat map renders as a proper grid. **STANDING zones always use `capacity`.**

```json
{ "action": "create-venue-map",
  "args": {
    "token": "u1_token",
    "eventId": "jazz_id",
    "venueName": "Main Venue",
    "zones": [
      { "name": "Standing Zone", "type": "STANDING", "basePrice": 50.0,  "capacity": 30 },
      { "name": "Seated Zone",   "type": "SEATED",   "basePrice": 100.0, "rows": 10, "columns": 10 }
    ]
  }
}
```

---

### `publish-event`
Makes an event visible in the public marketplace.

| Arg       | Required | Description |
|-----------|----------|-------------|
| `token`   | yes      | Authorized user token |
| `eventId` | yes      | Event ID (or bound name) |

```json
{ "action": "publish-event", "args": { "token": "u1_token", "eventId": "jazz_id" } }
```

---

### `set-sale-mode`
Sets how tickets are sold for an event.

| Arg       | Required | Description |
|-----------|----------|-------------|
| `token`   | yes      | Authorized user token |
| `eventId` | yes      | Event ID (or bound name) |
| `mode`    | yes      | `REGULAR`, `QUEUE`, or `RAFFLE` |

```json
{ "action": "set-sale-mode", "args": { "token": "u1_token", "eventId": "jazz_id", "mode": "QUEUE" } }
```

---

### `set-company-coupon-discount`
Sets a coupon-code discount policy on a company. When a buyer provides the matching code at checkout, the percentage is deducted from the ticket price. Caller must be a Founder or Owner.

| Arg          | Required | Description |
|--------------|----------|-------------|
| `token`      | yes      | Founder or Owner token |
| `companyId`  | yes      | Company ID (or bound name) |
| `code`       | yes      | Coupon code buyers must enter |
| `percentage` | yes      | Discount fraction in `[0.0, 1.0]` — e.g. `0.20` = 20% off |

```json
{ "action": "set-company-coupon-discount",
  "args": {
    "token": "u1_token",
    "companyId": "acme_id",
    "code": "SUMMER20",
    "percentage": 0.20
  }
}
```

---

### `suspend-company` / `reopen-company`
Suspends or reopens a company. Requires Founder or Owner privileges.

| Arg         | Required | Description |
|-------------|----------|-------------|
| `token`     | yes      | Founder or Owner token |
| `companyId` | yes      | Company ID (or bound name) |

---

### `add-to-cart`
Adds tickets to the active order for a user.

| Arg       | Required | Description |
|-----------|----------|-------------|
| `token`   | yes      | Authenticated user token |
| `eventId` | yes      | Event ID (or bound name) |
| `zoneId`  | yes      | Zone ID |
| `seatId`  | no       | Specific seat ID (for SEATED zones) |
| `quantity`| no       | Number of standing tickets (default: `1`) |

`bindTo` → stores the order ID.

---

### `checkout`
Completes the purchase and processes payment.

| Arg              | Required | Description |
|------------------|----------|-------------|
| `token`          | yes      | Authenticated user token |
| `orderId`        | yes      | Order ID (or bound name) |
| `paymentDetails` | yes      | Payment details string |
| `authCode`       | no       | Authorization code (for raffle winners) |

`bindTo` → stores the receipt ID.

---

## Error Handling

- If any operation fails the entire initialization aborts and the application does not start.
- The error log will identify which operation number failed and why.
- Fix the config file and restart the application.

---

## Full Example

See `config-example.json` at the project root for the complete demo scenario: four users, one company, one event with a standing zone and a seated zone, and a coupon discount.

To activate it, update `application.yml`:

```yaml
app:
  init:
    initial-state-file: config-example.json
```

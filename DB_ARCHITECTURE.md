# Database Architecture – Sadna Group 13A

## Table of Contents
1. [Stack Overview](#1-stack-overview)
2. [Layered Architecture](#2-layered-architecture)
3. [Full Flow: UI Click → Database Write](#3-full-flow-ui-click--database-write)
4. [File Types and What Each Does](#4-file-types-and-what-each-does)
5. [Two Persistence Strategies: Users vs. Everything Else](#5-two-persistence-strategies-users-vs-everything-else)
6. [Optimistic Locking](#6-optimistic-locking)
7. [The YAML Configuration Files and How Spring Reads Them](#7-the-yaml-configuration-files-and-how-spring-reads-them)
8. [Prod vs. Demo vs. Local Configuration](#8-prod-vs-demo-vs-local-configuration)
9. [Database Resilience and Health Checks](#9-database-resilience-and-health-checks)
10. [Database Tables Reference](#10-database-tables-reference)

---

## 1. Stack Overview

| Concern | Technology |
|---|---|
| ORM | Hibernate / Jakarta Persistence (JPA) via Spring Data JPA |
| Default DB (dev/demo) | H2 (embedded, file-based or in-memory) |
| Production DB | PostgreSQL (remote, cloud-hosted) |
| JSON serialization | Jackson (`ObjectMapper`) |
| Framework | Spring Boot 3.2.5, Java 17 |
| UI | Vaadin Flow (server-side Java components) |

There is **no REST API layer** — the UI is rendered server-side by Vaadin. The Presenter (UI logic) calls Application Services directly as regular Java method calls.

---

## 2. Layered Architecture

The project uses **4-layer clean / hexagonal architecture**. Each layer only talks to the layer directly below it. Arrows represent the direction of calls.

```
┌─────────────────────────────────────────────────┐
│  PRESENTATION LAYER (Vaadin)                    │
│  *View.java        — renders the HTML/UI        │
│  *Presenter.java   — handles button clicks      │
└──────────────────────┬──────────────────────────┘
                       │  calls
┌──────────────────────▼──────────────────────────┐
│  APPLICATION LAYER                              │
│  Services/*Service.java — orchestrates the flow │
│  DTO/*DTO.java          — data shapes for UI    │
│  Interfaces/I*.java     — gateway contracts     │
└──────────────────────┬──────────────────────────┘
                       │  calls
┌──────────────────────▼──────────────────────────┐
│  DOMAIN LAYER                                   │
│  Aggregates/**  — rich domain objects           │
│  Interfaces/IRepository.java — abstract DB      │
│  DomainServices/ — business rules               │
└──────────────────────┬──────────────────────────┘
                       │  implemented by
┌──────────────────────▼──────────────────────────┐
│  INFRASTRUCTURE LAYER                           │
│  RepositoryImpl/**Impl.java — JPA wiring        │
│  RepositoryImpl/jpa/*Entity.java — DB tables    │
│  RepositoryImpl/jpa/*JpaRepository.java         │
│  config/PersistenceConfig.java                  │
│  persistence/ — health checks, resilience       │
└─────────────────────────────────────────────────┘
```

The key rule: **the domain layer defines the repository interfaces but never references JPA or the database directly**. Infrastructure provides the actual implementations. This keeps business logic independent of the chosen database.

---

## 3. Full Flow: UI Click → Database Write

### Concrete example: user clicks "Checkout" on the cart page

**Step 1 — View (`CheckoutView.java`)**

The Vaadin view renders the page. When the user clicks the "Pay Now" button, the view collects the form values and delegates to the Presenter:

```java
// CheckoutView.java (presentation layer)
checkoutButton.addClickListener(e -> {
    presenter.handleCheckout(orderId, authCode, paymentDetails, this);
});
```

The View knows nothing about business rules or the database. Its only job is to render and forward events.

---

**Step 2 — Presenter (`CheckoutPresenter.java`)**

`src/main/java/.../presentation/views/cart/CheckoutPresenter.java`

The Presenter validates the raw input (card format, expiry date, CVV), reads the JWT token from the Vaadin session, then calls the Application Service:

```java
// CheckoutPresenter.java
public void handleCheckout(String orderId, String authCode,
                            PaymentDetails paymentDetails, CheckoutView view) {
    // ... input validation (card number format, expiry, CVV) ...
    String token = getToken(); // reads JWT from VaadinSession
    Result<OrderHistoryDTO> result = orderService.executeCheckout(token, orderId, code, paymentJson);
    if (result.isSuccess()) {
        view.displayReceipt(result.getData().orElse(null));
    } else {
        view.showError(result.getErrorMessage());
    }
}
```

The Presenter returns a `Result<T>` object (a simple success/failure wrapper). It never catches exceptions — errors come back as structured `Result.failure("message")` objects.

---

**Step 3 — Application Service (`OrderService.java`)**

`src/main/java/.../application/Services/OrderService.java`

This is the main orchestrator. It:
- Validates the JWT token via `IAuth`
- Loads domain objects from repositories
- Calls Domain Services for business rules
- Calls external gateways (payment, ticket issuance)
- Persists the result back via repositories

```java
// OrderService.java (annotated @Transactional)
@Transactional
public Result<OrderHistoryDTO> executeCheckout(String token, String orderId, ...) {
    String userId = authGateway.extractUserId(token);

    // 1. Load domain objects from repositories
    User user         = userRepository.findById(userId).orElseThrow(...);
    ActiveOrder order = orderRepository.findById(orderId).orElseThrow(...);
    Event event       = eventRepository.findById(order.getEventId()).orElseThrow(...);

    // 2. Domain service validates business rules (policies, permissions, seat availability)
    checkoutDomainService.validate(user, order, event, ...);

    // 3. Call external payment gateway
    paymentGateway.charge(paymentDetails);

    // 4. Call external ticket supplier
    ticketSupplier.issue(tickets);

    // 5. Persist the result — these are the actual DB writes
    event.markSeatsAsSold(order.getItems());  // mutates domain object
    eventRepository.save(event);              // → DB write

    OrderHistory history = OrderHistory.from(order);
    historyRepository.save(history);          // → DB write

    user.clearActiveOrder();
    userRepository.save(user);                // → DB write

    orderRepository.delete(order.getId());    // → DB delete

    return Result.success(toDTO(history));
}
```

All repository calls within a `@Transactional` method run in a single database transaction. If anything throws an exception, all writes roll back.

---

**Step 4 — Domain Repository Interface (`IEventRepository.java`)**

`src/main/java/.../domain/Interfaces/IEventRepository.java`

This interface lives in the domain layer and knows nothing about JPA or SQL. It defines **what** operations exist without specifying **how** they work:

```java
// IEventRepository.java
public interface IEventRepository {
    Optional<Event> findById(String id);
    void save(Event event);         // create or update
    void delete(String id);
    List<Event> findAll();
    List<Event> findPublished();
    List<Event> searchByTitle(String query);
    // ...
}
```

---

**Step 5 — Repository Implementation (`EventRepositoryImpl.java`)**

`src/main/java/.../infrastructure/RepositoryImpl/EventRepositoryImpl.java`

This class implements `IEventRepository`. It bridges the rich domain model to the simplified JPA Entity format, using **JSON serialization** for the full domain object:

```java
// EventRepositoryImpl.java
@Repository
public class EventRepositoryImpl implements IEventRepository {

    private final EventJpaRepository jpa;
    private final ObjectMapper objectMapper; // the "domainObjectMapper" bean

    @Override
    public synchronized void save(Event event) {
        // Optimistic lock check (see Section 6)
        Optional<EventEntity> stored = jpa.findById(event.getId());
        if (stored.isPresent()) {
            Event storedDomain = toDomain(stored.get());
            if (storedDomain.getVersion() >= event.getVersion()) {
                throw new OptimisticLockException(...);
            }
        }
        // Serialize the full domain object to a JSON string, store in "data" column
        jpa.save(new EventEntity(event.getId(), event.getCompanyId(),
                  event.getCategory(), event.isPublished(),
                  event.getTitle(), writeJson(event)));  // ← JSON blob
    }

    private String writeJson(Event event) {
        return objectMapper.writeValueAsString(event);  // domain → JSON
    }

    private Event toDomain(EventEntity entity) {
        return objectMapper.readValue(entity.getData(), Event.class);  // JSON → domain
    }
}
```

---

**Step 6 — JPA Entity (`EventEntity.java`)**

`src/main/java/.../infrastructure/RepositoryImpl/jpa/EventEntity.java`

This is a simple POJO annotated with JPA annotations. It represents exactly one row in the `events` table:

```java
@Entity
@Table(name = "events")
public class EventEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String companyId;

    @Column
    private String category;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private String title;

    @Lob                      // stored as a CLOB (large text)
    @Column(nullable = false)
    private String data;      // ← the full event JSON lives here
}
```

---

**Step 7 — Spring Data JPA Repository (`EventJpaRepository.java`)**

`src/main/java/.../infrastructure/RepositoryImpl/jpa/EventJpaRepository.java`

This is a Spring Data interface. Spring **auto-generates the SQL** at startup — you write method names, Spring produces the `SELECT`/`INSERT`/`UPDATE` statements:

```java
public interface EventJpaRepository extends JpaRepository<EventEntity, String> {
    List<EventEntity> findByCompanyId(String companyId);
    List<EventEntity> findByPublishedTrue();
    List<EventEntity> findByTitleContainingIgnoreCase(String titleFragment);
    List<EventEntity> findByCategory(String category);
}
```

Hibernate translates the `jpa.save(entity)` call into either an `INSERT` or `UPDATE` SQL statement, then executes it against the configured database (H2 or PostgreSQL depending on the active profile).

---

### Summary: the call chain in one view

```
CheckoutView.java (button click)
  └── CheckoutPresenter.java (input validation, reads JWT from session)
        └── OrderService.java (@Transactional, orchestrates everything)
              ├── IAuth.validateToken()         → authenticate user
              ├── IUserRepository.findById()    → load User from DB
              ├── IActiveOrderRepository.find() → load Order from DB
              ├── IEventRepository.findById()   → load Event from DB
              ├── CheckoutDomainService.validate() → business rules
              ├── IPaymentGateway.charge()      → external payment
              ├── ITicketSupplier.issue()        → external ticket system
              ├── IEventRepository.save(event)  → DB write
              ├── IOrderHistoryRepository.save() → DB write
              ├── IUserRepository.save(user)    → DB write
              └── IActiveOrderRepository.delete() → DB delete
                    │
                    ▼  (each repository call goes through)
              *RepositoryImpl.java (serializes domain → JPA entity)
                    │
                    ▼
              *JpaRepository.java (Spring Data, generates SQL)
                    │
                    ▼
              Hibernate ORM → SQL → H2 / PostgreSQL
```

---

## 4. File Types and What Each Does

### `*View.java` — Presentation Layer
**Package:** `presentation/views/**`

Vaadin UI components (buttons, forms, grids). Responsible only for **rendering** the screen and forwarding user actions to the Presenter. Never contains business logic. Never talks to services or the database directly.

---

### `*Presenter.java` — Presentation Layer
**Package:** `presentation/views/**`

Handles UI events. Reads the user's JWT token from the Vaadin session, does light **input validation** (e.g., card number format checks), then calls the appropriate Application Service. Translates `Result<DTO>` responses back into UI commands (`view.show...()`, `UI.getCurrent().navigate(...)`).

---

### `*DTO.java` (Data Transfer Objects) — Application Layer
**Package:** `application/DTO/`

Plain Java `record` types. DTOs are **read-only snapshots** of data that the UI needs to display — they are created by services and handed up to presenters and views. They do **not** contain business logic. They exist to decouple the rich domain model from what the UI actually needs to show.

Examples:
- `EventDTO` — id, title, description, category, saleMode (no venue map, no seat state)
- `OrderDTO` — orderId, status, items, totalBasePrice (no internal domain rules)
- `OrderHistoryDTO` — completed order receipt with items and total

DTOs flow **upward** (infrastructure → domain → service → presenter → view). Domain objects never flow upward past the service layer.

---

### `*Service.java` — Application Layer
**Package:** `application/Services/`

Spring `@Service` beans annotated with `@Transactional`. These are the **use-case orchestrators** — they implement one specific user action by coordinating repositories, domain services, and external gateways. They contain no business logic themselves; that lives in the domain. They do:
1. Validate the JWT token
2. Load domain objects from repositories
3. Call domain services / domain methods for rules
4. Call external gateways (payment, tickets)
5. Save mutated domain objects back to repositories
6. Return a `Result<DTO>` to the presenter

Key services:
| Service | Responsibility |
|---|---|
| `UserService` | Register, login, guest sessions, profile |
| `EventService` | Create/publish events, manage venue maps |
| `OrderService` | Add to cart, checkout, payment flow |
| `CompanyService` | Create company, appoint staff |
| `RaffleService` | Create raffle, register, draw winners |
| `QueueService` | Manage ticket queues |
| `ComplaintService` | File / respond to complaints |
| `AdminService` | Suspend users, system-level operations |
| `CartCleanupService` | Scheduled job: expire old carts |

---

### `I*Repository.java` (Domain Repository Interfaces) — Domain Layer
**Package:** `domain/Interfaces/`

Pure Java interfaces. Defined in the domain layer so that domain services and application services can depend on **abstractions** rather than JPA or SQL. The domain layer declares _what_ it needs from storage; infrastructure provides _how_.

These are the 13 repository interfaces:
- `IUserRepository`, `IAdminRepository`
- `IEventRepository`, `ICompanyRepository`
- `IActiveOrderRepository`, `IOrderHistoryRepository`, `IOrderQueueRepository`
- `IQueueRepository`, `IRaffleRepository`
- `IComplaintRepository`, `IInquiryRepository`
- `IPendingNotificationRepository`, `IUserNotificationRepository`

---

### Domain Aggregates — Domain Layer
**Package:** `domain/Aggregates/`

Rich domain objects that contain **both state and behavior** (not anemic data bags). They enforce business invariants — e.g., a `Seat` knows whether it is available and refuses to be held twice. They are the objects that get loaded from the DB, mutated, then saved back.

**Important:** `User` and `Admin` are JPA `@Entity` classes themselves (annotated with JPA annotations), so they can be stored and loaded directly by Hibernate. All other aggregates (`Event`, `ActiveOrder`, `Company`, etc.) are plain Java objects — they are serialized to a JSON blob by the repository implementation before going to the DB.

---

### `*RepositoryImpl.java` — Infrastructure Layer
**Package:** `infrastructure/RepositoryImpl/`

Spring `@Repository` beans that **implement the domain repository interfaces**. Each implementation bridges the domain model to the JPA layer. For most aggregates this means:
- **On save:** serialize the domain object to a JSON string → store in `data` column
- **On load:** read the JSON string from `data` column → deserialize back to domain object

The `save()` method is `synchronized` and includes an **optimistic lock check** before writing (see Section 6).

---

### `*Entity.java` (JPA Entities) — Infrastructure Layer
**Package:** `infrastructure/RepositoryImpl/jpa/`

Simple POJOs annotated with `@Entity`, `@Table`, `@Id`, `@Column`, etc. These define the **exact database schema** — one class = one table. For JSON-blob aggregates, the entity has a handful of indexed columns (id, companyId, published, etc.) plus one big `@Lob String data` column that holds the full serialized domain object.

For `User` (see above), the aggregate itself is the entity — there is no separate `UserEntity`.

---

### `*JpaRepository.java` (Spring Data Interfaces) — Infrastructure Layer
**Package:** `infrastructure/RepositoryImpl/jpa/`

Spring Data `JpaRepository<Entity, ID>` interfaces. Spring **generates all SQL at startup** by reading the method names. You never write `SELECT` statements. Methods like `findByPublishedTrue()` and `findByTitleContainingIgnoreCase(String)` are resolved to SQL by Spring Data's query derivation engine. These are only ever called by the `*RepositoryImpl.java` classes — never from the service layer.

---

### `PersistenceConfig.java` — Infrastructure Config
**Package:** `infrastructure/config/`

A Spring `@Configuration` class that defines a custom `ObjectMapper` bean named `"domainObjectMapper"`. This mapper is specifically tuned for domain aggregates:
- It reads/writes **private fields directly** (bypasses getters/setters)
- It detects `@JsonCreator` constructors (used by records and immutable objects)
- It handles `java.time.*` types via `JavaTimeModule`
- It ignores unknown JSON properties (for forward-compatibility)

This is kept separate from Spring MVC's default `ObjectMapper` so that serialization settings for domain objects don't conflict with HTTP response serialization.

---

### `persistence/*.java` — Infrastructure Resilience
**Package:** `infrastructure/persistence/`

A set of classes that implement **automatic recovery from database outages** (required by SL-6):

| Class | Role |
|---|---|
| `DataSourceHealthProbe` | Every 5 seconds, borrows a JDBC connection from the pool and calls `connection.isValid()`. If it fails, marks the system as degraded. |
| `DatabaseConnectionManager` | Holds the current connectivity state (UP / DEGRADED). Transitions between states based on probe results. |
| `PersistenceAvailabilityInvocationHandler` | A Java dynamic proxy that wraps repository method calls. Before executing, it checks `DatabaseConnectionManager` — if the DB is down, it throws an informative exception immediately instead of hanging. |
| `RepositoryAvailabilityBeanPostProcessor` | A Spring `BeanPostProcessor` that automatically wraps every `*RepositoryImpl` bean with the proxy above. No repository class needs to be modified. |
| `InMemoryDatabaseHealthProbe` | Test-only seam — not used in the running application. |

---

### `DemoDataSeeder.java` — Infrastructure (Demo Only)
**Package:** `infrastructure/`

A Spring `ApplicationRunner` bean activated only when `--spring.profiles.active=demo`. Runs after application startup and populates the database with realistic test data by calling the same Application Services a real user would. Uses `@Order(2)` so it runs after the system initializer.

---

## 5. Two Persistence Strategies: Users vs. Everything Else

The project uses two different approaches to store domain objects, depending on their complexity.

### Strategy A: Direct JPA Mapping (User, Admin)

`User` and `Admin` are annotated with JPA annotations (`@Entity`, `@Table`, `@Column`, etc.) **directly on the domain class**. Hibernate maps each field to a column one-to-one. Inheritance is handled with **single-table inheritance**: a `user_type` discriminator column tells Hibernate whether the row is a `Guest` or `Member`.

```
users table:
  id           (PK)
  username     (UNIQUE)
  user_type    (discriminator: GUEST / MEMBER)
  state        (ACTIVE / SUSPENDED / ...)
  active_order_id
  suspended_at
  suspended_until
  version
```

**Why:** `User` has a relatively flat structure that maps cleanly to columns. Direct JPA mapping allows filtering and joins on user fields efficiently.

---

### Strategy B: JSON Blob (Event, Company, Order, Raffle, etc.)

For complex aggregates with deep nested structures (e.g., an `Event` contains a `VenueMap` which contains many `Zone` objects each containing many `Seat` objects), a separate JPA `*Entity` class is used. The entity stores only a few **queryable columns** plus a `@Lob String data` column that holds the entire domain object as a JSON string.

```
events table:
  id         (PK)
  companyId  (queryable — used for "list events by company")
  category   (queryable — used for "filter by category")
  published  (queryable — used for "show only published events")
  title      (queryable — used for "search by title")
  data       (CLOB — full Event JSON: venue map, seats, policies, etc.)
```

**Why:** Avoids a complex join-heavy schema for deeply nested objects. The domain model can evolve freely without requiring database migrations. The tradeoff is that queries inside the blob (e.g., "find all events with a seat in row A") are not possible via SQL — you load the whole event and filter in Java.

The `domainObjectMapper` bean in `PersistenceConfig` handles the serialization — it reads private fields directly so that `final` fields and classes with no no-arg constructor can be round-tripped correctly.

---

## 6. Optimistic Locking

All aggregates have a `version` field (a counter that increments on every mutation). The `save()` method in every `*RepositoryImpl` is `synchronized` and checks this version before writing:

```java
public synchronized void save(Event event) {
    Optional<EventEntity> stored = jpa.findById(event.getId());
    if (stored.isPresent()) {
        Event storedDomain = toDomain(stored.get());
        if (storedDomain.getVersion() >= event.getVersion()) {
            throw new OptimisticLockException(
                "Conflict: stored=" + storedDomain.getVersion()
                + " incoming=" + event.getVersion());
        }
    }
    jpa.save(...);
}
```

**What this means:** if two concurrent requests both load version 5 of an event and both try to save, the second one will see that the stored version (now 6) is >= its incoming version (5) and will throw. This prevents **lost updates** — a scenario where one user's change silently overwrites another's.

---

## 7. The YAML Configuration Files and How Spring Reads Them

### What is a `.yml` file?

A `.yml` (YAML) file is Spring Boot's **externalized configuration** format. Instead of hardcoding values like a database URL or an admin password directly in your Java code, you write them in a YAML file and Spring injects them at startup. This means you can change the database host, credentials, or any behavior setting **without touching or recompiling any Java code**.

YAML is just a structured key-value format. The indentation creates a hierarchy:

```yaml
app:
  admin:
    username: admin       # → key is "app.admin.username", value is "admin"
    password: admin123    # → key is "app.admin.password"
  persistence:
    health-check-interval-ms: 5000   # → "app.persistence.health-check-interval-ms"
```

---

### How Spring Boot discovers and loads the files

Spring Boot's `SpringApplication.run()` (called in `SadnaApplication.java`) automatically looks for a file named **`application.yml`** in `src/main/resources/`. It loads it unconditionally, every time, as the base configuration.

On top of that, if you pass `--spring.profiles.active=prod` on the command line (or as an env var), Spring also loads **`application-prod.yml`** and **merges it over** the base. Properties in the profile-specific file win over the same keys in `application.yml`. Properties not mentioned in the profile file are inherited from the base unchanged.

```
application.yml          ← always loaded (base layer)
application-local.yml    ← layered on top if profile = local
application-demo.yml     ← layered on top if profile = demo
application-prod.yml     ← layered on top if profile = prod
```

The naming convention `application-{profile}.yml` is a Spring Boot standard — no configuration is needed to wire them up. Spring finds them automatically as long as they are in `src/main/resources/`.

---

### Who binds the YAML values to Java code?

There are two mechanisms, and the project uses both.

#### Mechanism 1: `@Value` — inject a single property

`@Value` is a Spring annotation that injects a single YAML value directly into a field. The syntax is `@Value("${yaml.key}")`.

Example from `PlatformBootstrap.java`:

```java
@Component
public class PlatformBootstrap implements ApplicationRunner {

    @Value("${app.admin.username}")   // reads "app.admin.username" from YAML
    private String adminUsername;

    @Value("${app.admin.password}")   // reads "app.admin.password" from YAML
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        systemService.initializePlatform(adminUsername, adminPassword);
    }
}
```

When Spring creates the `PlatformBootstrap` bean, it reads `app.admin.username` from the loaded YAML (considering active profiles) and sets the field. In demo mode, this becomes `yahlitheking` because `application-demo.yml` overrides that key. In all other modes it is `admin`.

`@Value` is best for one-off injections of simple scalar values. For a whole section of related properties, the second mechanism is cleaner.

---

#### Mechanism 2: `@ConfigurationProperties` — bind a whole section to a class

`@ConfigurationProperties(prefix = "app.init")` tells Spring to bind all keys under `app.init.*` to the fields of a Java class. Spring uses the setters to populate the object, then registers it as a bean so it can be injected anywhere.

Example from `SystemInitProperties.java`:

```java
@ConfigurationProperties(prefix = "app.init")
@Validated
public class SystemInitProperties {

    @Min(1)
    private int maxConcurrentUsersPerEvent;  // ← binds "app.init.max-concurrent-users-per-event"

    private String initialStateFile;         // ← binds "app.init.initial-state-file"

    // getters and setters...
}
```

This maps to the YAML:

```yaml
app:
  init:
    max-concurrent-users-per-event: 100
    initial-state-file: ""
```

Spring Boot converts `max-concurrent-users-per-event` (kebab-case) to `maxConcurrentUsersPerEvent` (camelCase) automatically — this is called **relaxed binding**.

The `@Validated` annotation means Spring also runs Jakarta Bean Validation (`@Min`, `@NotBlank`, etc.) against the bound values at startup. If `max-concurrent-users-per-event` is missing or less than 1, the application **refuses to start** and prints an error — a fail-fast design so misconfigured deployments are caught immediately.

---

#### What enables `@ConfigurationProperties` to be picked up?

The entry point `SadnaApplication.java` has `@ConfigurationPropertiesScan` on it:

```java
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan   // ← scans for all @ConfigurationProperties classes in the package
public class SadnaApplication {
    public static void main(String[] args) {
        SpringApplication.run(SadnaApplication.class, args);
    }
}
```

Without this annotation, Spring would not automatically register `@ConfigurationProperties` classes as beans. `@ConfigurationPropertiesScan` tells Spring to scan the entire package tree, find every class annotated with `@ConfigurationProperties`, bind the YAML values, and register each one as a singleton bean.

---

### The three `@ConfigurationProperties` classes in this project

| Class | YAML prefix | What it carries |
|---|---|---|
| `SystemInitProperties` | `app.init` | Max concurrent users per event, path to initial-state file |
| `ExternalSystemTimeoutProperties` | `app.external` | HTTP connect/read timeouts for calls to the payment and ticket systems |
| `ExternalPaymentProperties` | `app.external.payment` | Base URL of the WSEP payment gateway |

These are all in `application/config/` (application layer), which makes sense: they are application-level settings, not infrastructure details.

---

### Spring's built-in YAML bindings (datasource, JPA, H2 console)

Not every YAML key needs a custom Java class. Many keys under `spring.*` are read directly by Spring Boot's own auto-configuration, with no code in this project needed:

| YAML key | Who reads it |
|---|---|
| `spring.datasource.url` | Spring's `DataSourceAutoConfiguration` — creates the JDBC connection pool |
| `spring.datasource.driver-class-name` | Same — loads the correct JDBC driver (H2 or PostgreSQL) |
| `spring.jpa.hibernate.ddl-auto` | Spring's `HibernateJpaAutoConfiguration` — tells Hibernate whether to create/update/validate the schema |
| `spring.jpa.database-platform` | Same — selects the SQL dialect |
| `spring.h2.console.enabled` | Spring's `H2ConsoleAutoConfiguration` — enables the H2 web console at `/h2-console` |

These auto-configurations are provided by the Spring Boot starter dependencies (`spring-boot-starter-data-jpa`, `com.h2database:h2`, `org.postgresql:postgresql`). When those JARs are on the classpath, Spring Boot activates the corresponding auto-configuration classes, which read these `spring.*` keys and wire up the database connection, Hibernate session factory, and transaction manager — all without any `@Bean` definitions in the project's own code.

---

### Full picture: from YAML to a running database connection

```
application.yml  +  application-prod.yml   (active profile merges over base)
        │
        │  Spring Boot reads all files at startup
        ▼
Spring's Environment object  (unified key-value store in memory)
        │
        ├── spring.datasource.* ──────► DataSourceAutoConfiguration
        │                                  creates HikariCP connection pool
        │                                  → JDBC connections to H2 / PostgreSQL
        │
        ├── spring.jpa.* ─────────────► HibernateJpaAutoConfiguration
        │                                  creates SessionFactory + TransactionManager
        │                                  runs ddl-auto (create / update / validate)
        │
        ├── @Value("${app.admin.*}") ─► PlatformBootstrap field injection
        │                                  used at startup to create the admin user
        │
        └── @ConfigurationPropertiesScan
               └── SystemInitProperties    (prefix: app.init)
               └── ExternalSystemTimeoutProperties  (prefix: app.external)
               └── ExternalPaymentProperties        (prefix: app.external.payment)
                      → injected as beans wherever needed
```

---

## 8. Prod vs. Demo vs. Local Configuration

Spring profiles (`--spring.profiles.active=<profile>`) select which `application-<profile>.yml` overrides are applied on top of `application.yml`.

### Default (no profile)
**File:** `src/main/resources/application.yml`

```yaml
datasource:
  url: jdbc:h2:file:./data/sadna-db;AUTO_SERVER=TRUE
  driver-class-name: org.h2.Driver
jpa:
  database-platform: org.hibernate.dialect.H2Dialect
  hibernate.ddl-auto: update   # auto-create/alter tables on startup
h2.console.enabled: true       # accessible at /h2-console
```

- H2 **file-based** database, stored in `./data/sadna-db`
- Schema is auto-updated on every startup (Hibernate adds missing columns/tables)
- H2 web console enabled for inspection
- Data persists between restarts

---

### Local profile (`--spring.profiles.active=local`)
**File:** `src/main/resources/application-local.yml`

```yaml
datasource:
  url: jdbc:h2:mem:sadna;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
  driver-class-name: org.h2.Driver
jpa:
  hibernate.ddl-auto: create-drop   # schema built on startup, dropped on shutdown
```

- H2 **in-memory** database — nothing is saved to disk
- Schema is rebuilt fresh every restart
- Best for development when you want a clean slate

---

### Demo profile (`--spring.profiles.active=demo`)
**File:** `src/main/resources/application-demo.yml`

```yaml
app:
  admin:
    username: yahlitheking
    password: roee0
```

This file only overrides the admin credentials. It activates the `DemoDataSeeder` bean via `@Profile("demo")`, which seeds:
- 6 test users: alice, bob, carol, frank, eve, dave (password: `pass123`)
- 2 companies: SoundWave Entertainment, Stellar Experiences
- 5 events across all sale modes (REGULAR, QUEUE, RAFFLE)

The underlying **database is still H2 file-based** from `application.yml` — demo mode does not change the database engine, only the seeded data and admin credentials.

---

### Production profile (`--spring.profiles.active=prod`)
**File:** `src/main/resources/application-prod.yml`

```yaml
datasource:
  url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:sadna}
  username: ${DB_USERNAME:sadna}
  password: ${DB_PASSWORD}          # NO default — must be set as env var
  driver-class-name: org.postgresql.Driver
jpa:
  database-platform: org.hibernate.dialect.PostgreSQLDialect
  hibernate.ddl-auto: validate      # NEVER auto-alter the schema
```

Key differences from demo/local:
- **PostgreSQL** instead of H2 — remote, cloud-hosted database
- All connection details come from **environment variables** (no secrets in code)
- `ddl-auto: validate` — Hibernate checks that the schema matches the entities but **never changes it**. Schema migrations must be done manually (e.g., by a DBA or migration script)
- Real external gateways activated: `WsepPaymentGateway` and `ExternalTicketSupplier` (stub implementations used in all non-prod profiles are annotated `@Profile("!prod")` so they are excluded)

To run in prod mode:
```powershell
$env:DB_HOST="your-host"; $env:DB_PASSWORD="secret"
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

---

### Side-by-side comparison

| | Default | Local | Demo | **Prod** |
|---|---|---|---|---|
| Database | H2 file | H2 memory | H2 file | **PostgreSQL** |
| Persistence | Yes | No | Yes | **Yes** |
| Schema strategy | `update` | `create-drop` | `update` | **`validate`** |
| H2 console | Yes | Yes | Yes | **No** |
| Demo data seeded | No | No | **Yes** | No |
| Admin credentials | admin/admin123 | admin/admin123 | yahlitheking/roee0 | admin/admin123 |
| Payment gateway | Stub | Stub | Stub | **Real (WSEP)** |
| Ticket supplier | Stub | Stub | Stub | **Real** |
| DB secrets | Hardcoded | Hardcoded | Hardcoded | **Env vars only** |

---

## 9. Database Resilience and Health Checks

The system automatically recovers from database outages without restarting (required by SL-6).

**How it works:**

1. `DataSourceHealthProbe` runs on a scheduled timer (every 5 seconds by default, configurable via `app.persistence.health-check-interval-ms`). It calls `connection.isValid(2)` — a lightweight JDBC check that doesn't execute SQL.

2. If the check fails, `DatabaseConnectionManager` transitions to **degraded mode**.

3. `RepositoryAvailabilityBeanPostProcessor` wraps every `*RepositoryImpl` bean in a JDK dynamic proxy at startup. On every repository call, the proxy checks the `DatabaseConnectionManager` state. If degraded, it throws an informative exception immediately (rather than waiting for a connection timeout).

4. When the health probe succeeds again, `DatabaseConnectionManager` transitions back to **normal mode**, and repository calls flow through as usual — no restart needed.

This is entirely transparent to the service layer — `OrderService`, `EventService`, etc. never know it exists.

---

## 10. Database Tables Reference

| Table | Key columns | Persistence strategy |
|---|---|---|
| `users` | id (PK), username (UNIQUE), user_type (DISCRIMINATOR), state, active_order_id, suspended_at, suspended_until, version | Direct JPA (columns per field) |
| `admins` | id (PK), username (UNIQUE), state, version | Direct JPA (single-table inheritance) |
| `events` | id (PK), companyId, category, published, title, **data** (CLOB) | JSON blob |
| `companies` | id (PK), **data** (CLOB) | JSON blob |
| `active_orders` | id (PK), userId, **data** (CLOB) | JSON blob |
| `order_history` | id (PK), userId, **data** (CLOB) | JSON blob |
| `raffles` | id (PK), eventId, companyId, **data** (CLOB) | JSON blob |
| `ticket_queues` | id (PK), **data** (CLOB) | JSON blob |
| `complaints` | id (PK), userId, **data** (CLOB) | JSON blob |
| `inquiries` | id (PK), userId, **data** (CLOB) | JSON blob |
| `pending_notifications` | id (PK), **data** (CLOB) | JSON blob |
| `user_notifications` | id (PK), userId, **data** (CLOB) | JSON blob |

Schema is auto-created by Hibernate in all non-prod profiles. In prod, the schema must already exist and `ddl-auto: validate` confirms it matches.

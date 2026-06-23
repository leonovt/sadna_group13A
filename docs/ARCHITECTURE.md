# System Architecture — Deep Dive

A complete guide to understanding, explaining, and presenting the architecture of the
Event Ticketing System (Group 13A, Software Engineering Workshop).

---

## Table of Contents

1. [Why This Architecture?](#1-why-this-architecture)
2. [The Four Layers](#2-the-four-layers)
   - [Presentation Layer](#21-presentation-layer)
   - [Application Layer](#22-application-layer)
   - [Domain Layer](#23-domain-layer)
   - [Infrastructure Layer](#24-infrastructure-layer)
3. [All Interfaces and Their Implementations](#3-all-interfaces-and-their-implementations)
4. [Communication Flows](#4-communication-flows)
5. [Library Choices and Why](#5-library-choices-and-why)
6. [Key Design Patterns](#6-key-design-patterns)
7. [Cross-Cutting Concerns](#7-cross-cutting-concerns)

---

## 1. Why This Architecture?

### The Core Idea: Layered / Clean Architecture

The system is built on **Clean Architecture** (also called Hexagonal or Ports-and-Adapters
architecture). The central rule is:

> **Dependencies always point inward.** Outer layers know about inner layers; inner layers
> know nothing about outer layers.

```
Presentation  →  Application  →  Domain  ←  Infrastructure
```

The arrow between Domain and Infrastructure is reversed on purpose. The Domain *defines*
interfaces (Repository interfaces, Policy interfaces), and the Infrastructure *implements*
them. This means the core business logic never imports a JPA class, a Spring annotation,
or a Vaadin component.

### Why we chose this approach

| Motivation | How this architecture helps |
|---|---|
| **Testability** | Domain and Application layers have zero framework dependencies, so they can be unit-tested with plain Java + Mockito — no Spring context needed. |
| **Swappable infrastructure** | Switching from H2 to PostgreSQL required zero code changes — only a config profile switch. The same is true for payment gateways (stub vs. WSEP). |
| **Requirement traceability** | Each Application Service maps almost 1-to-1 with a functional requirement area (UC 1.x), making it easy to find the code for any use case. |
| **Independent evolution** | The UI team can change Vaadin views without touching business logic; the persistence team can change JPA mappings without touching domain rules. |
| **Failure isolation** | The persistence resilience proxy (see §7) can wrap every repository call uniformly because all repositories are accessed through interfaces — no layer ever calls JPA directly. |

### The dependency inversion in practice

Two groups of interfaces make the inversion work:

1. **Repository Interfaces** — defined in the Domain layer, implemented in Infrastructure.
   The domain says "I need something that can save/retrieve an `Event`", and infrastructure
   provides it without the domain knowing anything about JPA or SQL.

2. **Gateway Interfaces** — defined in the Application layer, implemented in Infrastructure.
   Application Services say "I need something that can process a payment", and infrastructure
   wires in either a real payment adapter or a stub for tests.

---

## 2. The Four Layers

### 2.1 Presentation Layer

**Package:** `com.sadna.group13a.presentation`
**Framework:** Vaadin 24
**Pattern:** Model-View-Presenter (MVP)

#### What it does

The Presentation layer owns the entire user interface. It has no business logic — its only
jobs are to render data on screen and to forward user actions to Presenters.

#### How MVP works here

```
User clicks button
      ↓
  View method called
      ↓
  View calls Presenter
      ↓
  Presenter calls Application Service
      ↓
  Presenter receives Result<T>
      ↓
  Presenter calls View.update(...)
      ↓
  View re-renders
```

- **View** — a Vaadin component class. Builds the UI, registers click/input listeners,
  exposes `update*(...)` methods that the Presenter calls. Never calls services directly.
- **Presenter** — plain Java class (no Vaadin import). Accepts the View via constructor,
  receives user actions, calls the appropriate Application Service, and pushes `Result<T>`
  back to the View. This is the only class in the presentation layer that can be unit-tested.

#### All Presenter / View Pairs

| Feature Area | Presenters | Views |
|---|---|---|
| **Auth** | `LoginPresenter`, `RegisterPresenter` | `LoginView`, `RegisterView` |
| **Home** | `HomePresenter` | `HomeView` |
| **Cart / Checkout** | `CartPresenter`, `CheckoutPresenter` | `CartView`, `CheckoutView` |
| **Event Detail** | `EventDetailPresenter` | `EventDetailView` |
| **Queue** | `QueuePresenter` | `QueueView` |
| **Member** | `MemberDashboardPresenter`, `OrderHistoryPresenter`, `ProfilePresenter`, `RafflePresenter`, `ComplaintPresenter`, `InquiryPresenter` | `MemberDashboardView`, `OrderHistoryView`, `ProfileView`, `RaffleView`, `ComplaintView`, `InquiryView` |
| **Company** | `CompanyDashboardPresenter`, `CompanyOrderHistoryPresenter`, `CompanyPoliciesPresenter`, `CompanyRafflePresenter`, `EventManagementPresenter`, `EventPoliciesPresenter`, `PolicyManagementPresenter`, `SalesReportPresenter`, `StaffManagementPresenter`, `CompanyInquiriesPresenter` | *(matching Views)* |
| **Admin** | `AdminDashboardPresenter`, `AdminAnalyticsPresenter`, `AdminQueuePresenter`, `AdminUserManagementPresenter`, `AdminComplaintsPresenter` | *(matching Views)* |

**Total: 32 Presenter/View pairs.**

#### Vaadin WebSocket Push

`PushConfig` enables Vaadin's server-push (`@Push`). This allows the notification system
to push messages to connected browser sessions in real time without the client polling.
When the backend publishes a `QueueTurnArrivedEvent`, the UI is updated immediately via
a WebSocket push without requiring a page refresh.

---

### 2.2 Application Layer

**Package:** `com.sadna.group13a.application`

#### What it does

The Application layer is the **orchestration** layer. It knows the steps needed to fulfill
a use case — which aggregates to load, which domain services to invoke, how to call the
payment gateway, when to publish a domain event — but it does not contain business rules
itself. Those live in the Domain.

Every public method on every Application Service returns `Result<T>`: a simple wrapper
that holds either a success value or a structured error string. This means the Presentation
layer never sees exceptions from business logic — it always handles a predictable
success/failure envelope.

#### All Application Services

| Service | Responsibility |
|---|---|
| `UserService` | Registration, login/logout, guest sessions, member account management, suspension/reactivation |
| `EventService` | Create/edit/publish events, manage venue maps and zones, event search |
| `OrderService` | Add to cart, remove from cart, checkout (seat reservation → payment → ticket issuance), refunds, order history |
| `CompanyService` | Create companies, appoint/remove staff, manage staff permissions, handle nominations |
| `AdminService` | Suspend/ban/reactivate users, force-close companies, send admin messages, view analytics |
| `QueueService` | Open/close virtual queues, manage queue position, advance the queue, handle timeouts |
| `RaffleService` | Open/close raffles, register participants, draw winners, validate auth codes |
| `SystemService` | Platform-wide analytics: active users, total events, revenue totals |
| `SystemLogService` | Audit logging of significant actions |
| `ComplaintService` | Submit, view, and resolve user complaints |
| `InquiryService` | Submit and answer company inquiries |
| `CartCleanupService` | Scheduled job (every minute): finds carts that have been held too long, releases the seats back to the event, and fires a `CartExpiredEvent` |
| `SuspensionExpiryJob` | Scheduled job: finds time-limited suspensions that have elapsed and reactivates those accounts automatically |

#### Gateway Interfaces (defined here, implemented in Infrastructure)

These interfaces allow Application Services to call external systems without ever importing
an infrastructure class.

| Interface | Purpose |
|---|---|
| `IAuth` | JWT token generation (`generateToken`) and validation (`validateToken`) |
| `IPasswordEncoder` | Password hashing (`encode`) and verification (`matches`) |
| `IPaymentGateway` | Payment processing (`processPayment`), full refund (`refundPayment`), partial refund (`refundPartial`), connectivity check (`isConnected`) |
| `ITicketSupplier` | Issue physical/digital tickets after a successful checkout |
| `INotificationService` | 20+ typed notification methods — one per notification scenario (queue turn, order complete, ban, raffle, etc.) |

#### DTOs (Data Transfer Objects)

DTOs carry data across layer boundaries. They are plain Java classes with no behaviour.

| Domain Area | DTOs |
|---|---|
| User | `UserDTO`, `SuspensionDTO` |
| Event | `EventDTO`, `VenueMapDTO`, `ZoneDTO`, `ZoneCreationDTO`, `SeatDTO` |
| Order | `OrderDTO`, `OrderItemDTO`, `OrderHistoryDTO`, `OrderHistoryItemDTO`, `PaymentDetails` |
| Company | `CompanyDTO`, `StaffMemberDTO`, `AdminActionRequestDTO`, `SalesReportDTO` |
| Queue | `TicketQueueDTO`, `QueueStatusDTO` |
| Raffle | `RaffleDTO`, `RaffleRegistrationDTO`, `RaffleResultDTO`, `WinningTicketDTO` |
| System | `SystemAnalyticsDTO` |
| Complaint | `ComplaintDTO` |
| Inquiry | `InquiryDTO` |

#### Event Listeners

Spring's `@EventListener` mechanism connects domain events to side effects without
coupling them to the service that raised the event.

| Listener | What it handles |
|---|---|
| `NotificationEventListener` | Subscribes to all 18+ domain events that require user-facing notifications. On each event it calls the appropriate `INotificationService` method. |
| `CompanyEventListener` | Handles `CompanySuspendedEvent`, `CompanyReopenedEvent`, `CompanyClosedByAdminEvent` — updates company status in the database as a side effect of the event. |

---

### 2.3 Domain Layer

**Package:** `com.sadna.group13a.domain`
**Rule:** Pure Java. Zero Spring annotations. Zero JPA annotations. Zero infrastructure imports.

#### What it does

The Domain layer is the heart of the system. All business invariants live here:
- "A seat can only be reserved if it is AVAILABLE."
- "An order cannot be checked out if the company is suspended."
- "A raffle cannot be drawn twice."
- "A staff member can only grant permissions they themselves hold."

#### Aggregates

An **Aggregate** is a cluster of objects treated as a single unit. The **Aggregate Root**
is the only entry point — external code never directly mutates a child object.

##### User Aggregate

Root: `User` (abstract)  
Subtypes: `Guest` (browsing, no account), `Member` (registered), `Admin` (platform administrator)

The subtype is determined at runtime by the **State Pattern**: `User` delegates behaviour
to a `UserTypeState` object (`GuestState`, `MemberState`, or `AdminState`). This means a
`Guest` can be "upgraded" to a `Member` by replacing the state object — no new entity needed.

Enums: `UserRole` (GUEST, MEMBER, ADMIN), `UserState` (ACTIVE, SUSPENDED, BANNED)

##### Event Aggregate

Root: `Event`  
Children: `VenueMap` → `Zone` (abstract) → `SeatedZone` (with `Seat` children) or `StandingZone`

The Event aggregate enforces seating invariants. When a seat is reserved, `Event` finds the
correct zone and seat, checks the `SeatStatus` (AVAILABLE → HELD), and throws
`SeatUnavailableException` if already taken. No service outside this aggregate can
directly flip seat statuses.

`Event` uses **optimistic locking** (`@Version` field) so two concurrent reservations of
the same event cause one to get an `OptimisticLockException`, which the service layer retries.

Enums: `EventSaleMode` (REGULAR, QUEUE, RAFFLE), `ZoneType`, `SeatStatus` (AVAILABLE, HELD, SOLD, RELEASED)

##### ProductionCompany Aggregate

Root: `ProductionCompany`  
Children: `CompanyStaffMember` (role + permissions), `AppointmentRequest` (pending nomination)

Manages the company's internal hierarchy. Invariants: only a FOUNDER or a MANAGER with the
right `CompanyPermission` can appoint other staff. The aggregate enforces this without the
service layer knowing the details.

Enums: `CompanyStatus` (ACTIVE, SUSPENDED, CLOSED), `CompanyRole` (FOUNDER, MANAGER),  
`CompanyPermission` (MANAGE\_EVENTS, MANAGE\_POLICIES, MANAGE\_DISCOUNTS, VIEW\_REPORTS)

##### ActiveOrder Aggregate

Root: `ActiveOrder`  
Children: `OrderItem` (event + zone + seat reference)

Represents a shopping cart. Expires after 10 minutes (configured via `app.seat.hold-duration-minutes`).
`CartCleanupService` scans for expired carts and fires `CartExpiredEvent`.

##### OrderHistory Aggregate

Root: `OrderHistory`  
Children: `OrderHistoryItem` (receipt line: price, discount applied, final total)

Immutable once created. Represents a completed, paid purchase. Includes the transaction ID
from the payment gateway and a list of external ticket IDs from the ticket supplier.

##### TicketQueue Aggregate

Root: `TicketQueue`  
Children: `QueueTicket` (user position + expiry)

Virtual waiting queue for high-demand events with `EventSaleMode.QUEUE`. When it is a user's
turn, `QueueTurnArrivedEvent` is published and the user is notified in real time. If they do
not complete checkout in time, their queue ticket expires and the next person is advanced.

##### Raffle Aggregate

Root: `Raffle`  
Children: `AuthorizationCode` (one per winner — a short code the winner types at checkout)

Lottery-style sale (`EventSaleMode.RAFFLE`). Members register, the company draws winners,
each winner receives an `AuthorizationCode` by notification, and must use it within a time
window to complete their purchase.

Enum: `RaffleStatus` (OPEN, DRAWN, CLOSED)

##### Complaint Aggregate

Root: `Complaint`

Represents a formal complaint submitted by a member. Admin staff can view and resolve
complaints through the Admin Dashboard.

Enum: `ComplaintStatus`

##### Inquiry Aggregate

Root: `Inquiry`

Represents a question submitted by a member to a specific company. The company can reply,
which fires `InquiryAnsweredEvent` and notifies the member.

Enum: `InquiryStatus`

---

#### Domain Services

Domain Services hold business logic that spans multiple aggregates and does not naturally
belong to any single one. They are pure Java, receive aggregates as arguments (never fetch
from a repository themselves), and are instantiated as Spring beans via `DomainServiceConfig`.

| Domain Service | What it does |
|---|---|
| `CartDomainService` | Atomically reserves a list of seats: if any single reservation fails, all already-reserved seats in the batch are released before re-throwing the exception. Prevents partial cart state. |
| `CheckoutDomainService` | Drives the full checkout sequence on an `ActiveOrder`: validates `PurchasePolicy`, calculates discount via `DiscountPolicy`, transitions seats HELD→SOLD, creates the `OrderHistory` receipt. |
| `TicketingAccessDomainService` | Validates that a user's queue ticket is at the front of the queue (for QUEUE events) or that an authorization code is valid and unused (for RAFFLE events). |
| `CompanyStaffDomainService` | Enforces the staff appointment/removal rules: permission checks, duplicate prevention, founder protection. |
| `EventSearchDomainService` | Filters a list of `Event` objects against a set of search criteria (title, date range, price range, availability). |
| `VenueMapFactory` | Creates and initializes a `VenueMap` from a list of zone definitions, validating zone types and capacities. |

---

#### Policy Engine (Composite Pattern)

Both policy trees (`PurchasePolicy` and `DiscountPolicy`) are built using the **Composite
pattern**: leaf nodes hold a single rule; composite nodes (`AndPolicy`, `OrPolicy`,
`MaxDiscountPolicy`, `AdditiveDiscountPolicy`) hold child policies and combine their results.
This lets you build arbitrarily complex rules from simple building blocks.

**Purchase Policies** — gate whether a purchase is allowed at all.

| Class | Rule |
|---|---|
| `AllowAllPolicy` | Always permits. Default for new events/companies. |
| `AgeRestrictionPolicy` | Buyer's age must be ≥ N. |
| `MinTicketsPolicy` | Order must contain at least N tickets. |
| `MaxTicketsPolicy` | Order must contain at most N tickets. |
| `AndPolicy` | All child policies must pass. |
| `OrPolicy` | At least one child policy must pass. |

**Discount Policies** — calculate a percentage reduction on the order total.

| Class | Rule |
|---|---|
| `NoDiscountPolicy` | 0% discount. Default. |
| `SimpleDiscount` | Fixed % discount unconditionally. |
| `ConditionalDiscount` | Fixed % discount if a condition (age, quantity, etc.) is met. |
| `CouponDiscount` | Discount applies when a specific coupon code is entered. |
| `MaxDiscountPolicy` | Evaluates all children, takes the highest discount. |
| `AdditiveDiscountPolicy` | Sums all children's discounts (capped at 100%). |

Policies are attached at two levels:
- **Event-level policy** — applies to purchases of that specific event's tickets.
- **Company-level policy** — applies to all events run by that company (layered on top of event policy).

Policies are **serialized to JSON** for persistence (`PurchasePolicyConverter`,
`DiscountPolicyConverter`) and **deserialized** when the aggregate is loaded from the database.

---

#### Repository Interfaces

Twelve interfaces define the persistence contract. The domain declares what it needs;
infrastructure delivers it.

| Interface | Aggregate it manages |
|---|---|
| `IUserRepository` | `User` |
| `IEventRepository` | `Event` |
| `ICompanyRepository` | `ProductionCompany` |
| `IActiveOrderRepository` | `ActiveOrder` |
| `IOrderHistoryRepository` | `OrderHistory` |
| `IRaffleRepository` | `Raffle` |
| `IQueueRepository` | `TicketQueue` |
| `IOrderQueueRepository` | `ItemQueue` (internal queue management) |
| `IAdminRepository` | `Admin` |
| `IPendingNotificationRepository` | Pending offline notifications |
| `IComplaintRepository` | `Complaint` |
| `IInquiryRepository` | `Inquiry` |

---

#### Domain Events

Domain Events are plain Java objects (no annotations) that signal something significant
happened. Services publish them via Spring's `ApplicationEventPublisher` *after* the
main transaction commits, so listeners see a consistent state.

| Category | Events |
|---|---|
| User | `UserSuspendedEvent`, `UserReactivatedEvent`, `UserBannedEvent` |
| Order | `OrderCompletedEvent`, `CheckoutFailedEvent`, `CartExpiredEvent`, `RefundIssuedEvent` |
| Event | `EventSoldOutEvent`, `EventCancelledEvent`, `EventRescheduledEvent` |
| Company | `CompanySuspendedEvent`, `CompanyClosedByAdminEvent`, `CompanyReopenedEvent` |
| Queue | `QueueTurnArrivedEvent` |
| Raffle | `RaffleDrawnEvent`, `RaffleWonEvent` |
| Staff / Permission | `StaffNominatedEvent`, `StaffRemovedEvent`, `PermissionsUpdatedEvent` |
| Admin | `AdminMessageEvent` |
| Inquiry | `InquiryAnsweredEvent` |

---

#### Shared Domain Concepts

| Class / Enum | Purpose |
|---|---|
| `PurchaseContext` | Value object passed to `PurchasePolicy.evaluate()` — holds buyer age, order quantity, coupon code, etc. |
| `DiscountContext` | Value object passed to `DiscountPolicy.calculate()` — same buyer data for discount evaluation. |
| `OrderStatus` | PENDING, COMPLETED, FAILED, CANCELLED |
| `DomainException` (and subtypes) | Base for all domain exceptions. Subtypes: `EntityNotFoundException`, `OptimisticLockException`, `SeatUnavailableException`, `PaymentFailedException`, `TicketIssuanceException`, `PermissionDeniedException`, `PersistenceUnavailableException`, `AuthenticationException` |

---

### 2.4 Infrastructure Layer

**Package:** `com.sadna.group13a.infrastructure`

#### What it does

Implements all interfaces defined by inner layers. Contains everything that requires a
framework, an external library, or an I/O call: JPA entities, Spring Data repositories,
JWT handling, payment API calls, WebSocket sessions, and application bootstrap.

---

#### Repository Implementations

Each `*RepositoryImpl` class:
1. Implements the domain `I*Repository` interface (satisfying the domain's contract).
2. Is wrapped by the **persistence availability proxy** (see §7).
3. Delegates all actual storage to a Spring Data `JpaRepository`.

| Impl | Domain Interface | JPA Repository |
|---|---|---|
| `UserRepositoryImpl` | `IUserRepository` | `UserJpaRepository` |
| `EventRepositoryImpl` | `IEventRepository` | `EventJpaRepository` |
| `CompanyRepositoryImpl` | `ICompanyRepository` | `CompanyJpaRepository` |
| `ActiveOrderRepositoryImpl` | `IActiveOrderRepository` | `ActiveOrderJpaRepository` |
| `OrderHistoryRepositoryImpl` | `IOrderHistoryRepository` | `OrderHistoryJpaRepository` |
| `RaffleRepositoryImpl` | `IRaffleRepository` | `RaffleJpaRepository` |
| `QueueRepositoryImpl` | `IQueueRepository` | *(custom JPA)* |
| `AdminRepositoryImpl` | `IAdminRepository` | `AdminJpaRepository` |
| `PendingNotificationRepositoryImpl` | `IPendingNotificationRepository` | `NotificationJpaRepository` |
| `ComplaintRepositoryImpl` | `IComplaintRepository` | `ComplaintJpaRepository` |
| `InquiryRepositoryImpl` | `IInquiryRepository` | `InquiryJpaRepository` |

**Special JPA notes:**
- `User` uses **single-table inheritance** (`@Inheritance(SINGLE_TABLE)`) — Guest, Member,
  and Admin rows all live in one `users` table with a `dtype` discriminator column.
- `EventEntity` and `CompanyEntity` store their policy trees in JSON columns, converted
  by `PurchasePolicyConverter` / `DiscountPolicyConverter` (JPA `AttributeConverter`).
- `Event` and `ProductionCompany` use `@Version` for **optimistic locking**.

---

#### External Adapters

| Interface | Production Implementation | Test/Dev Stub |
|---|---|---|
| `IAuth` | `AuthImpl` — JJWT HS256 tokens | *(same class, key from config)* |
| `IPasswordEncoder` | `PasswordEncoderImpl` — Spring Security BCrypt | *(same class)* |
| `IPaymentGateway` | `WsepPaymentGateway` — REST calls to WSEP payment service (`@Profile("prod")`) | `StubPaymentGateway` — always succeeds, returns a fake transaction ID (`@Profile("!prod")`) |
| `ITicketSupplier` | `ExternalTicketSupplier` — REST calls to WSEP ticket API (`@Profile("prod")`) | `StubTicketSupplier` — returns fake ticket IDs (`@Profile("!prod")`) |
| `INotificationService` | `WebSocketNotificationService` — push via Vaadin's `@Push` WebSocket | `InMemoryNotificationService` — logs in memory (fallback) |

**Profile-driven switching:** No code change is needed to swap stubs for real gateways.
Running with `-Dspring-boot.run.profiles=prod` activates the real adapters; every other
profile uses stubs. Spring's `@Profile` annotation on the class handles selection.

---

#### Notification System

```
Domain Event published
        ↓
NotificationEventListener (Application Layer)
        ↓
INotificationService.notifyXxx(userId, ...)
        ↓
WebSocketNotificationService
        ↓
NotificationBroadcaster ─── online? ──→ Vaadin Push → Browser
                       └── offline? ──→ PendingNotificationRepositoryImpl
                                               ↓
                                     Delivered on next login
```

`NotificationBroadcaster` maintains a map of `userId → Vaadin UI session`. When
`WebSocketNotificationService` calls it, it looks up the session and pushes the
notification string directly to the browser. If the user is offline the notification is
persisted and delivered when they next open the application.

---

#### Persistence Resilience

The system stays partially operational even when the database is unavailable.

| Class | Role |
|---|---|
| `DataSourceHealthProbe` / `InMemoryDatabaseHealthProbe` | Probe that checks whether the real datasource is reachable. |
| `DatabaseConnectionManager` | Tracks current DB health; updates state on probe results. |
| `PersistenceAvailabilityInvocationHandler` | JDK dynamic proxy that intercepts every method call on a repository. If the DB is down, it throws `PersistenceUnavailableException` immediately instead of hanging. |
| `RepositoryAvailabilityBeanPostProcessor` | Spring `BeanPostProcessor` that wraps every `*RepositoryImpl` bean in the above proxy at startup. |

Result: Application Services catch `PersistenceUnavailableException` and return a
user-friendly `Result.failure(...)` message. The system does not crash.

---

#### Bootstrap and Initialization

`PlatformBootstrap` runs at startup (`@PostConstruct`):

1. **External system check** — pings `IPaymentGateway.isConnected()` and the ticket supplier
   endpoint. Startup fails if either is unreachable (in `prod` profile).
2. **Admin seeding** — creates the bootstrap admin account from `app.admin.*` config if it
   does not already exist.
3. **Initial-state replay** — if `app.init.initial-state-file` is configured, loads a JSON
   file describing a series of use-case operations (register users, create companies, add
   events…) and executes them through the Application layer. All-or-nothing: any failure
   aborts startup with a clear error.
4. **Scheduled jobs start** — `CartCleanupService` and `SuspensionExpiryJob` begin running.

---

## 3. All Interfaces and Their Implementations

### Repository Interfaces (Domain Layer → Infrastructure)

| Interface (domain) | Implementation (infrastructure) | Spring Data Repo |
|---|---|---|
| `IUserRepository` | `UserRepositoryImpl` | `UserJpaRepository` |
| `IEventRepository` | `EventRepositoryImpl` | `EventJpaRepository` |
| `ICompanyRepository` | `CompanyRepositoryImpl` | `CompanyJpaRepository` |
| `IActiveOrderRepository` | `ActiveOrderRepositoryImpl` | `ActiveOrderJpaRepository` |
| `IOrderHistoryRepository` | `OrderHistoryRepositoryImpl` | `OrderHistoryJpaRepository` |
| `IRaffleRepository` | `RaffleRepositoryImpl` | `RaffleJpaRepository` |
| `IQueueRepository` | `QueueRepositoryImpl` | *(JPA)* |
| `IOrderQueueRepository` | `OrderQueueRepositoryImpl` | *(JPA)* |
| `IAdminRepository` | `AdminRepositoryImpl` | `AdminJpaRepository` |
| `IPendingNotificationRepository` | `PendingNotificationRepositoryImpl` | `NotificationJpaRepository` |
| `IComplaintRepository` | `ComplaintRepositoryImpl` | `ComplaintJpaRepository` |
| `IInquiryRepository` | `InquiryRepositoryImpl` | `InquiryJpaRepository` |

### Gateway Interfaces (Application Layer → Infrastructure)

| Interface (application) | Production Implementation | Dev/Test Implementation |
|---|---|---|
| `IAuth` | `AuthImpl` | `AuthImpl` (same) |
| `IPasswordEncoder` | `PasswordEncoderImpl` | `PasswordEncoderImpl` (same) |
| `IPaymentGateway` | `WsepPaymentGateway` (`@Profile("prod")`) | `StubPaymentGateway` (`@Profile("!prod")`) |
| `ITicketSupplier` | `ExternalTicketSupplier` (`@Profile("prod")`) | `StubTicketSupplier` (`@Profile("!prod")`) |
| `INotificationService` | `WebSocketNotificationService` (`@Primary`) | `InMemoryNotificationService` (fallback) |

### Domain-internal interfaces

| Interface | Implementations |
|---|---|
| `PurchasePolicy` | `AllowAllPolicy`, `AgeRestrictionPolicy`, `MinTicketsPolicy`, `MaxTicketsPolicy`, `AndPolicy`, `OrPolicy` |
| `DiscountPolicy` | `NoDiscountPolicy`, `SimpleDiscount`, `ConditionalDiscount`, `CouponDiscount`, `MaxDiscountPolicy`, `AdditiveDiscountPolicy` |
| `UserTypeState` | `GuestState`, `MemberState`, `AdminState` |

---

## 4. Communication Flows

### 4.1 Typical Request Flow (Add to Cart)

```
User clicks "Add to cart" in EventDetailView
        ↓
EventDetailView calls EventDetailPresenter.onAddToCart(eventId, zoneId, seatId)
        ↓
EventDetailPresenter calls OrderService.addToCart(token, eventId, zoneId, seatId)
        ↓
OrderService:
  1. Validates token → IAuth.validateToken(token) → userId
  2. Loads user      → IUserRepository.findById(userId)
  3. Loads event     → IEventRepository.findById(eventId)
  4. Loads/creates cart → IActiveOrderRepository.findByUserId(userId)
  5. Calls CartDomainService.reserveSeatsAtomically(event, zoneId, [seatId], userId)
        ↓
     CartDomainService:
       event.reserveSeat(zoneId, seatId, userId)   ← mutates SeatStatus AVAILABLE→HELD
       if any seat fails → rolls back already-reserved seats → throws
  6. Saves event     → IEventRepository.save(event)   ← JPA flush
  7. Saves cart      → IActiveOrderRepository.save(cart)
  8. Returns Result.success(orderDTO)
        ↓
EventDetailPresenter receives Result<OrderDTO>
        ↓
EventDetailView.showCartUpdated(orderDTO)   ← re-renders seat map
```

### 4.2 Checkout Flow

```
CheckoutPresenter.onConfirmPurchase(token, orderId, paymentDetails)
        ↓
OrderService.checkout(token, orderId, paymentDetails)
        ↓
  1. Load user, event, activeOrder
  2. CheckoutDomainService.checkout(activeOrder, event, member, paymentDetails):
        a. PurchasePolicy.evaluate(PurchaseContext)   → throws if rejected
        b. DiscountPolicy.calculate(DiscountContext)  → returns discount %
        c. Seat HELD → SOLD on the event aggregate
        d. Creates OrderHistory with receipt items
  3. IPaymentGateway.processPayment(total, paymentDetails) → transactionId
     └── if fails → refund immediately, throw PaymentFailedException
  4. ITicketSupplier.issueTickets(request) → ticketIds
     └── if fails → refund immediately, throw TicketIssuanceException
  5. Save OrderHistory, delete ActiveOrder
  6. ApplicationEventPublisher.publishEvent(new OrderCompletedEvent(...))
        ↓
     NotificationEventListener.onOrderCompleted(event)
        ↓
     INotificationService.notifyOrderCompleted(userId, receiptId, totalPaid)
        ↓
     WebSocketNotificationService → NotificationBroadcaster → Vaadin Push → Browser
```

### 4.3 Domain Event Flow

```
Application Service
        │
        │ ApplicationEventPublisher.publishEvent(new SomeEvent(...))
        ↓
Spring Event Bus (synchronous by default, after transaction commit)
        │
        ├── NotificationEventListener.onSomeEvent(e) → INotificationService.notifyXxx(...)
        │
        └── CompanyEventListener.onSomeEvent(e) → updates company state if needed
```

### 4.4 Real-Time Notification Delivery

```
INotificationService.notifyQueueTurnArrived(userId, eventId, expiresAt)
        ↓
WebSocketNotificationService.notifyQueueTurnArrived(...)
        ↓
NotificationBroadcaster.send(userId, messageString)
        │
        ├─ [user online] → UI.access(session) { show notification dialog } → Vaadin Push → WebSocket → Browser
        └─ [user offline] → PendingNotificationRepositoryImpl.save(PendingNotificationEntity)
                                          ↓
                              On next session open: delivered and deleted
```

---

## 5. Library Choices and Why

### Spring Boot 3.2.5

**What it provides:** Auto-configured application context, dependency injection, transaction
management, scheduled tasks, application event publishing, REST client, and profile-based
configuration.

**Why we chose it:** Spring Boot is the de-facto standard for Java backend applications.
It lets us focus on business logic rather than wiring infrastructure beans manually. The
`@Profile` mechanism is exactly what we needed for stub vs. real gateway switching.
`@Transactional` and `ApplicationEventPublisher` integrate cleanly with our architecture.

### Vaadin 24.3.13

**What it provides:** A full-stack Java UI framework where server-side Java code produces
HTML/CSS/JS. Includes built-in WebSocket push (`@Push`) and a rich component library.

**Why we chose it:** The course requirement was a Java application with a UI. Vaadin lets
us write the UI entirely in Java — no separate JavaScript codebase, no REST API glue layer
needed between frontend and backend. Server-side rendering means all authentication and
authorization checks happen on the server. The built-in push mechanism was essential for
the real-time queue notification requirement.

### Spring Data JPA + Hibernate

**What it provides:** Object-relational mapping (ORM), JPQL/SQL query generation, schema
management, and an easy `JpaRepository` interface for CRUD operations.

**Why we chose it:** JPA removes the need to write raw SQL for standard CRUD operations
and provides `@Transactional` integration out of the box. Hibernate's schema generation
(`ddl-auto: create-drop`) makes local development fast — no migration scripts needed
for H2. Switching to `ddl-auto: validate` in production means the schema is managed
separately and not accidentally altered by the app.

### H2 (local) + PostgreSQL (production)

**H2** is an in-memory database that requires zero installation — perfect for local
development and CI tests. The application starts in seconds, no Docker required.

**PostgreSQL** is a robust, production-grade relational database used in the remote
deployment. The switch from H2 to PostgreSQL is a config/profile change — no code
changes — because the application speaks standard JPA.

### JJWT 0.12.5 (HS256)

**What it provides:** JSON Web Token generation, signing, and validation using
HMAC-SHA256.

**Why we chose it:** Session tokens need to be stateless (no server-side session store)
so the application scales horizontally. JWT lets us encode the `userId` into a signed
token that the client sends with every request. The token is verified by `AuthImpl`
without any database lookup. The `IAuth` interface means we could swap the implementation
(e.g. to RSA-signed tokens) without changing any service code.

### Spring Security Crypto (BCrypt)

**What it provides:** The `BCryptPasswordEncoder` — one of the most widely deployed
password hashing algorithms.

**Why we chose it:** BCrypt is specifically designed for password storage: it is slow by
design (making brute-force attacks expensive), automatically salted, and output is
fixed-length. We use only the `spring-security-crypto` module — not the full Spring
Security framework — because our authentication flow is handled by JWT tokens, not
Spring Security sessions.

### Jackson (jackson-databind + jackson-datatype-jsr310)

**What it provides:** JSON serialization/deserialization. `jackson-datatype-jsr310` adds
support for `LocalDate`, `LocalDateTime`, and other `java.time` types.

**Why we chose it:** We serialize the Policy tree objects to JSON for storage in the
database (via JPA `AttributeConverter`). Jackson's tree/ObjectMapper API makes it easy
to write recursive serializers for the composite policy structures. It also serializes
DTOs to/from JSON for the initial-state file loader.

### SLF4J + Logback

**What it provides:** A logging facade (SLF4J) and concrete logging implementation
(Logback, the default in Spring Boot).

**Why we chose it:** `Logger` instances are obtained via `LoggerFactory.getLogger(...)` 
in every service and domain service. Using the SLF4J facade means we could swap Logback
for Log4j2 without changing any application code. Logback is fast, configurable, and
ships as Spring Boot's default.

### JUnit 5 + Mockito + JaCoCo

**JUnit 5** is the standard Java test framework. `@Test`, `@BeforeEach`, parameterized
tests, and extension model are used throughout.

**Mockito** is used to mock interfaces (repository interfaces, gateway interfaces) in unit
tests. Because all dependencies are injected via constructor and hidden behind interfaces,
mocking is straightforward.

**JaCoCo** enforces a **75% line coverage minimum** as a build gate (`mvn verify`). The
presentation layer and `DemoDataSeeder` are excluded from the threshold because they are
not practically unit-testable without a running browser. Coverage reports are generated
under `target/site/jacoco/`.

### Maven Surefire Plugin

Runs all `*Test.java` and `*Tests.java` classes automatically during `mvn test`. The
explicit configuration ensures JUnit 5 is used (not the older JUnit 4 runner that
Surefire defaults to in some versions).

---

## 6. Key Design Patterns

### Repository Pattern

Every aggregate has an interface (`I*Repository`) that defines how to retrieve and persist it.
This completely decouples business logic from persistence technology. The domain says "I need
a `findById`" — it does not care whether the answer comes from JPA, an in-memory map, or a
remote API.

**Where:** `domain/Interfaces/` (interface) + `infrastructure/RepositoryImpl/` (JPA implementation)

### Composite Pattern (Policy Engine)

Both `PurchasePolicy` and `DiscountPolicy` follow the Composite pattern. Leaf nodes hold
a single rule; composite nodes (`AndPolicy`, `OrPolicy`, `MaxDiscountPolicy`,
`AdditiveDiscountPolicy`) hold a list of children and combine their results. Callers treat
a single rule and a complex tree of rules identically — they just call `evaluate()`.

This makes policy configuration expressive and extensible: adding a new rule type is adding
one new class that implements the interface.

**Where:** `domain/policies/`

### Model-View-Presenter (MVP)

Separates UI rendering (View) from presentation logic (Presenter). The View is a thin
Vaadin component; the Presenter is plain Java and fully testable.

**Where:** `presentation/views/`

### Strategy Pattern (Policies)

`PurchasePolicy` and `DiscountPolicy` are interchangeable strategies plugged into `Event`
and `ProductionCompany`. The checkout domain service calls `policy.evaluate(context)` —
it does not know which concrete policy is attached.

**Where:** `domain/policies/`, `domain/DomainServices/CheckoutDomainService.java`

### State Pattern (User Types)

`User` delegates type-specific behaviour to a `UserTypeState` object. This avoids large
`if (userType == GUEST) ... else if (userType == MEMBER) ...` chains inside `User`.

**Where:** `domain/Aggregates/User/`

### Adapter Pattern (External Gateways)

`WsepPaymentGateway` and `ExternalTicketSupplier` adapt an external REST API to the
`IPaymentGateway` / `ITicketSupplier` interface the application expects. The application
never knows the HTTP details.

**Where:** `infrastructure/WsepPaymentGateway.java`, `infrastructure/ExternalTicketSupplier.java`

### Factory Pattern

`VenueMapFactory` encapsulates the complex construction logic for a `VenueMap` (validating
zone types, initializing seats, etc.) so that callers just provide zone definitions.

**Where:** `domain/DomainServices/VenueMapFactory.java`

### Observer / Event Pattern (Domain Events)

Application Services publish domain events via Spring's `ApplicationEventPublisher`.
Listeners in the Application layer subscribe without the publisher knowing who is listening.
This decouples the core flow (checkout) from side effects (notifications, company state
updates).

**Where:** `domain/Events/`, `application/EventListeners/`

### DTO Pattern

Data Transfer Objects prevent leaking domain internals to the presentation layer and
prevent the presentation layer's concerns from bleeding into the domain. Presenters receive
DTOs, not aggregate roots.

**Where:** `application/DTO/`

---

## 7. Cross-Cutting Concerns

### Authentication (JWT)

Every request from the UI that requires a logged-in user passes a JWT token string to the
Application Service. The service calls `IAuth.validateToken(token)` to extract the `userId`.
No HTTP session is used — the token is held in Vaadin's session on the client side.

Token lifetime is configurable (`app.jwt.expiration-ms`, default 1 hour). On expiry the
user must log in again.

### Transactions

Spring's `@Transactional` annotation on Application Service methods ensures that all
repository writes within one use case succeed or fail together. If `OrderService.checkout`
fails after updating the event but before saving the order history, the transaction rolls
back and the seat status reverts to HELD.

Domain events are published *after* the transaction commits (Spring's `TransactionSynchronizationManager`) to ensure listeners see committed data.

### Optimistic Locking

`Event` and `ProductionCompany` are high-contention aggregates. Both use a `@Version` field.
When two concurrent requests both try to modify the same event (e.g. two users reserving the
last seat), one transaction succeeds and the other gets an `OptimisticLockException`, which
the service layer catches and retries or surfaces as a failure result.

### Persistence Resilience

The `PersistenceAvailabilityInvocationHandler` wraps every repository bean in a JDK proxy.
Before each call, the proxy checks `DatabaseConnectionManager` for current DB health. If
the DB is marked unavailable, it throws `PersistenceUnavailableException` immediately
(no hanging connection attempt). Application Services catch this and return a
`Result.failure("Service temporarily unavailable")` to the user.

`DataSourceHealthProbe` periodically pings the database and updates the health flag,
allowing the system to recover automatically once the database comes back online.

### Real-Time Notifications (WebSocket)

Vaadin's `@Push` annotation enables a persistent WebSocket connection between the server
and the browser. `NotificationBroadcaster` calls `UI.access(session, ...)` to push a
message to a specific user's browser session from any server thread. If the user is
offline, the message is persisted in the `PendingNotification` table and delivered the
next time they load the application.

### Code Coverage Gate

JaCoCo is configured as a Maven build plugin with a **75% line coverage** minimum enforced
on `mvn verify`. The presentation package and `DemoDataSeeder` are excluded. Any commit
that drops coverage below the threshold will fail the CI pipeline, preventing untested
code from reaching `main`.

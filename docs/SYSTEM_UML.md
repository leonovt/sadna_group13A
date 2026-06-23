# System UML — Event Ticketing Platform (Group 13A)

---

## 1. Architecture Overview

Four-layer clean architecture. Dependency arrows always point **inward** — outer layers depend on inner layers, never the reverse.

```mermaid
graph TD
    subgraph PRES["① Presentation  (Vaadin 24 + MVP)"]
        UI["32 View / Presenter pairs\nAuth · Home · Cart · Event · Queue\nMember · Company · Admin"]
    end

    subgraph APP["② Application  (Spring Services)"]
        SVC["11 Application Services\nUserService · EventService · OrderService · CompanyService\nAdminService · QueueService · RaffleService\nComplaintService · InquiryService · SystemService\n+ CartCleanupService · SuspensionExpiryJob"]
        GW["Gateway Interfaces  (defined here, implemented in Infra)\nIAuth · IPasswordEncoder · IPaymentGateway\nITicketSupplier · INotificationService"]
        EV["Event Listeners\nNotificationEventListener · CompanyEventListener"]
    end

    subgraph DOM["③ Domain  (Pure Java — no Spring / JPA)"]
        AGG["10 Aggregates\nUser · Event · ProductionCompany · ActiveOrder\nOrderHistory · TicketQueue · Raffle · Admin\nComplaint · Inquiry"]
        DS["6 Domain Services\nCartDomain · CheckoutDomain · TicketingAccess\nCompanyStaff · EventSearch · VenueMapFactory"]
        REPO_IF["12 Repository Interfaces\nIUserRepo · IEventRepo · ICompanyRepo · IActiveOrderRepo\nIOrderHistoryRepo · IRaffleRepo · IQueueRepo · IOrderQueueRepo\nIAdminRepo · IPendingNotificationRepo · IComplaintRepo · IInquiryRepo"]
        POL["Policy Engine  (Composite Pattern)\nPurchasePolicy tree · DiscountPolicy tree"]
    end

    subgraph INFRA["④ Infrastructure  (Spring Boot / JPA / WebSocket)"]
        RIMPL["11 Repository Implementations\n*RepositoryImpl → *JpaRepository  (Spring Data)"]
        EXT["External Adapters\nAuthImpl · PasswordEncoderImpl\nWsepPaymentGateway / StubPaymentGateway\nExternalTicketSupplier / StubTicketSupplier"]
        NOTIF["Notification\nWebSocketNotificationService → NotificationBroadcaster\nPendingNotificationRepositoryImpl  (offline delivery)"]
    end

    UI -->|"calls"| SVC
    SVC -->|"uses"| REPO_IF
    SVC -->|"uses"| DS
    SVC -->|"uses"| GW
    SVC -.->|"publishes"| EV
    DS -->|"operates on"| AGG
    DS -->|"applies"| POL
    REPO_IF -.->|"implemented by"| RIMPL
    GW -.->|"implemented by"| EXT
    GW -.->|"implemented by"| NOTIF
```

---

## 2. Domain Aggregates

Each box is an **Aggregate Root** — the only external entry point. Children are owned exclusively by the root.

```mermaid
classDiagram

    class User {
        <<abstract>>
        Guest | Member | Admin
        state: UserTypeState
    }
    class Event {
        saleMode: REGULAR|QUEUE|RAFFLE
        VenueMap → Zone → Seat
        purchasePolicy
        discountPolicy
    }
    class ProductionCompany {
        status: ACTIVE|SUSPENDED|CLOSED
        staff: CompanyStaffMember[]
        appointments: AppointmentRequest[]
        purchasePolicy
        discountPolicy
    }
    class ActiveOrder {
        expires after 10 min
        items: OrderItem[]
    }
    class OrderHistory {
        immutable receipt
        items: OrderHistoryItem[]
    }
    class TicketQueue {
        tickets: QueueTicket[]
    }
    class Raffle {
        status: OPEN|DRAWN|CLOSED
        codes: AuthorizationCode[]
    }
    class Complaint {
        status: ComplaintStatus
    }
    class Inquiry {
        status: InquiryStatus
    }
    class Admin

    User --> Event : browses / orders
    User --> ActiveOrder : owns
    User --> OrderHistory : owns
    User --> TicketQueue : joins
    User --> Raffle : registers
    ProductionCompany --> Event : manages
```

### User Aggregate — State Pattern

```mermaid
classDiagram
    class User { <<abstract>> }
    class Guest
    class Member
    class Admin
    class UserTypeState { <<interface>> }
    class GuestState
    class MemberState
    class AdminState

    Guest --|> User
    Member --|> User
    Admin --|> User
    User --> UserTypeState
    GuestState ..|> UserTypeState
    MemberState ..|> UserTypeState
    AdminState ..|> UserTypeState
```

### Event Aggregate — Venue Structure

```mermaid
classDiagram
    class Event
    class VenueMap
    class Zone { <<abstract>> }
    class SeatedZone
    class StandingZone
    class Seat {
        status: AVAILABLE|HELD|SOLD
    }

    Event *-- VenueMap
    VenueMap *-- Zone
    SeatedZone --|> Zone
    StandingZone --|> Zone
    SeatedZone *-- Seat
```

---

## 3. Policy Engine  (Composite Pattern)

Both trees can be composed to arbitrary depth. `Event` and `ProductionCompany` each carry one `PurchasePolicy` root and one `DiscountPolicy` root. Serialized to JSON for persistence.

```mermaid
classDiagram
    class PurchasePolicy { <<interface>> evaluate(ctx) bool }
    class AllowAllPolicy
    class AgeRestrictionPolicy
    class MinTicketsPolicy
    class MaxTicketsPolicy
    class AndPolicy { children: PurchasePolicy[] }
    class OrPolicy  { children: PurchasePolicy[] }

    AllowAllPolicy      ..|> PurchasePolicy
    AgeRestrictionPolicy ..|> PurchasePolicy
    MinTicketsPolicy    ..|> PurchasePolicy
    MaxTicketsPolicy    ..|> PurchasePolicy
    AndPolicy           ..|> PurchasePolicy
    OrPolicy            ..|> PurchasePolicy
    AndPolicy *-- PurchasePolicy
    OrPolicy  *-- PurchasePolicy

    class DiscountPolicy { <<interface>> calculate(ctx) double }
    class NoDiscountPolicy
    class SimpleDiscount
    class ConditionalDiscount
    class CouponDiscount
    class MaxDiscountPolicy   { children: DiscountPolicy[] }
    class AdditiveDiscountPolicy { children: DiscountPolicy[] }

    NoDiscountPolicy        ..|> DiscountPolicy
    SimpleDiscount          ..|> DiscountPolicy
    ConditionalDiscount     ..|> DiscountPolicy
    CouponDiscount          ..|> DiscountPolicy
    MaxDiscountPolicy       ..|> DiscountPolicy
    AdditiveDiscountPolicy  ..|> DiscountPolicy
    MaxDiscountPolicy      *-- DiscountPolicy
    AdditiveDiscountPolicy *-- DiscountPolicy
```

---

## 4. Application Services — Key Dependencies

| Service | Repository Interfaces | Gateway Interfaces |
|---|---|---|
| `UserService` | IUserRepository | IAuth, IPasswordEncoder |
| `EventService` | IEventRepository, ICompanyRepository | — |
| `OrderService` | IActiveOrderRepo, IOrderHistoryRepo, IEventRepo, IUserRepo, ICompanyRepo, IQueueRepo, IRaffleRepo | IPaymentGateway, ITicketSupplier, IAuth |
| `CompanyService` | ICompanyRepository, IUserRepository, IEventRepository | — |
| `AdminService` | IUserRepository, ICompanyRepository, IAdminRepository | — |
| `QueueService` | IQueueRepository, IOrderQueueRepository, IEventRepository, IUserRepository | — |
| `RaffleService` | IRaffleRepository, IEventRepository, IUserRepository | — |
| `ComplaintService` | IComplaintRepository, IUserRepository | — |
| `InquiryService` | IInquiryRepository, ICompanyRepository, IUserRepository | — |
| `SystemService` | IUserRepo, IEventRepo, ICompanyRepo, IOrderHistoryRepo | — |
| `CartCleanupService` | IActiveOrderRepository, IEventRepository | — |

---

## 5. Infrastructure Wiring

### Repository chain:  Domain Interface → Impl → Spring Data JPA

```mermaid
graph LR
    subgraph Domain
        I1["IUserRepository"]
        I2["IEventRepository"]
        I3["ICompanyRepository"]
        I4["IActiveOrderRepository"]
        I5["IOrderHistoryRepository"]
        I6["IRaffleRepository"]
        I7["IQueueRepository"]
        I8["IAdminRepository"]
        I9["IPendingNotificationRepository"]
        I10["IComplaintRepository"]
        I11["IInquiryRepository"]
    end

    subgraph Impl
        R1["UserRepositoryImpl"]
        R2["EventRepositoryImpl"]
        R3["CompanyRepositoryImpl"]
        R4["ActiveOrderRepositoryImpl"]
        R5["OrderHistoryRepositoryImpl"]
        R6["RaffleRepositoryImpl"]
        R7["QueueRepositoryImpl"]
        R8["AdminRepositoryImpl"]
        R9["PendingNotificationRepositoryImpl"]
        R10["ComplaintRepositoryImpl"]
        R11["InquiryRepositoryImpl"]
    end

    subgraph JPA["Spring Data JPA"]
        J1["UserJpaRepository"]
        J2["EventJpaRepository"]
        J3["CompanyJpaRepository"]
        J4["ActiveOrderJpaRepository"]
        J5["OrderHistoryJpaRepository"]
        J6["RaffleJpaRepository"]
        J8["AdminJpaRepository"]
        J9["NotificationJpaRepository"]
        J10["ComplaintJpaRepository"]
        J11["InquiryJpaRepository"]
    end

    I1 -.->|impl| R1 --> J1
    I2 -.->|impl| R2 --> J2
    I3 -.->|impl| R3 --> J3
    I4 -.->|impl| R4 --> J4
    I5 -.->|impl| R5 --> J5
    I6 -.->|impl| R6 --> J6
    I7 -.->|impl| R7
    I8 -.->|impl| R8 --> J8
    I9 -.->|impl| R9 --> J9
    I10 -.->|impl| R10 --> J10
    I11 -.->|impl| R11 --> J11
```

### Gateway Adapters

| Interface | Production | Dev / Test |
|---|---|---|
| `IAuth` | `AuthImpl` (JJWT HS256) | same |
| `IPasswordEncoder` | `PasswordEncoderImpl` (BCrypt) | same |
| `IPaymentGateway` | `WsepPaymentGateway` (`@Profile("prod")`) | `StubPaymentGateway` |
| `ITicketSupplier` | `ExternalTicketSupplier` (`@Profile("prod")`) | `StubTicketSupplier` |
| `INotificationService` | `WebSocketNotificationService` (primary) | `InMemoryNotificationService` (fallback) |

---

## 6. Real-Time Notification Flow

```mermaid
graph TD
    A["Application Service\npublishes Domain Event"] --> B["NotificationEventListener"]
    B --> C["INotificationService"]
    C --> D["WebSocketNotificationService"]
    D --> E{"User online?"}
    E -->|yes| F["NotificationBroadcaster\nUI.access → Vaadin Push → Browser"]
    E -->|no| G["PendingNotificationRepositoryImpl\n(stored in DB)"]
    G --> H["Delivered on next login"]
```

---

## 7. Domain Events  (21 total)

Published by Application Services via Spring `ApplicationEventPublisher` after transaction commit.

| Category | Events |
|---|---|
| User | `UserSuspendedEvent`, `UserReactivatedEvent`, `UserBannedEvent` |
| Order | `OrderCompletedEvent`, `CheckoutFailedEvent`, `CartExpiredEvent`, `RefundIssuedEvent` |
| Event | `EventSoldOutEvent`, `EventCancelledEvent`, `EventRescheduledEvent` |
| Company | `CompanySuspendedEvent`, `CompanyClosedByAdminEvent`, `CompanyReopenedEvent` |
| Queue | `QueueTurnArrivedEvent` |
| Raffle | `RaffleDrawnEvent`, `RaffleWonEvent` |
| Staff | `StaffNominatedEvent`, `StaffRemovedEvent`, `PermissionsUpdatedEvent` |
| Admin | `AdminMessageEvent` |
| Inquiry | `InquiryAnsweredEvent` |

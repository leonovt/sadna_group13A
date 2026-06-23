# Application Layer

Orchestrates use cases by combining Domain aggregates, Domain Services,
Repository Interfaces, and external Gateway Interfaces.
All public methods return `Result<T>` — a thin wrapper that carries either
the success value or a structured error message.

## Services and Their Dependencies

```mermaid
graph LR
    subgraph SERVICES["Application Services"]
        UserService
        EventService
        OrderService
        CompanyService
        AdminService
        QueueService
        RaffleService
        SystemService
        SystemLogService
        ComplaintService
        InquiryService
        CartCleanupService
        SuspensionExpiryJob
    end

    subgraph DOMAIN_REPOS["Domain Repository Interfaces"]
        IUserRepository
        IEventRepository
        ICompanyRepository
        IActiveOrderRepository
        IOrderHistoryRepository
        IRaffleRepository
        IQueueRepository
        IOrderQueueRepository
        IAdminRepository
        IPendingNotificationRepository
        IComplaintRepository
        IInquiryRepository
    end

    subgraph GATEWAY["Gateway Interfaces"]
        IAuth
        IPasswordEncoder
        IPaymentGateway
        ITicketSupplier
        INotificationService
    end

    UserService --> IUserRepository
    UserService --> IAuth
    UserService --> IPasswordEncoder

    EventService --> IEventRepository
    EventService --> ICompanyRepository

    OrderService --> IActiveOrderRepository
    OrderService --> IOrderHistoryRepository
    OrderService --> IEventRepository
    OrderService --> IUserRepository
    OrderService --> ICompanyRepository
    OrderService --> IQueueRepository
    OrderService --> IRaffleRepository
    OrderService --> IPaymentGateway
    OrderService --> ITicketSupplier
    OrderService --> IAuth

    CompanyService --> ICompanyRepository
    CompanyService --> IUserRepository
    CompanyService --> IEventRepository

    AdminService --> IUserRepository
    AdminService --> ICompanyRepository
    AdminService --> IAdminRepository

    QueueService --> IQueueRepository
    QueueService --> IOrderQueueRepository
    QueueService --> IEventRepository
    QueueService --> IUserRepository

    RaffleService --> IRaffleRepository
    RaffleService --> IEventRepository
    RaffleService --> IUserRepository

    SystemService --> IUserRepository
    SystemService --> IEventRepository
    SystemService --> ICompanyRepository
    SystemService --> IOrderHistoryRepository

    ComplaintService --> IComplaintRepository
    ComplaintService --> IUserRepository

    InquiryService --> IInquiryRepository
    InquiryService --> ICompanyRepository
    InquiryService --> IUserRepository

    CartCleanupService --> IActiveOrderRepository
    CartCleanupService --> IEventRepository

    SuspensionExpiryJob --> IUserRepository
```

## Gateway Interfaces

```mermaid
classDiagram
    class IAuth {
        <<interface>>
        generateToken(userId) String
        validateToken(token) String
    }
    class IPasswordEncoder {
        <<interface>>
        encode(raw) String
        matches(raw, encoded) boolean
    }
    class IPaymentGateway {
        <<interface>>
        charge(details) void
        refund(details) void
    }
    class ITicketSupplier {
        <<interface>>
        issueTickets(request) List
    }
    class INotificationService {
        <<interface>>
        sendToUser(userId, message) void
        broadcast(message) void
    }
```

## DTOs

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

## Event Listeners

| Listener | Domain Events Handled |
|---|---|
| `NotificationEventListener` | `UserSuspendedEvent`, `UserBannedEvent`, `UserReactivatedEvent`, `OrderCompletedEvent`, `CartExpiredEvent`, `CheckoutFailedEvent`, `RefundIssuedEvent`, `EventSoldOutEvent`, `EventCancelledEvent`, `EventRescheduledEvent`, `QueueTurnArrivedEvent`, `RaffleWonEvent`, `RaffleDrawnEvent`, `StaffNominatedEvent`, `StaffRemovedEvent`, `PermissionsUpdatedEvent`, `AdminMessageEvent`, `InquiryAnsweredEvent` |
| `CompanyEventListener` | `CompanySuspendedEvent`, `CompanyReopenedEvent`, `CompanyClosedByAdminEvent` |

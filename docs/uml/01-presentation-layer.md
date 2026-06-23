# Presentation Layer

Built on **Vaadin** with the **Model-View-Presenter (MVP)** pattern.
The View owns the UI components and fires user events; the Presenter handles logic and calls
Application Services; results flow back to the View wrapped in `Result<T>`.

## MVP Pattern

```mermaid
classDiagram
    class View {
        <<Vaadin Component>>
    }
    class Presenter
    class ApplicationService {
        <<Application Layer>>
    }

    View --> Presenter
    Presenter --> View
    Presenter --> ApplicationService
```

## Presenter / View Pairs by Feature Area

```mermaid
graph TD
    subgraph AUTH["Auth"]
        LoginPresenter --- LoginView
        RegisterPresenter --- RegisterView
    end

    subgraph HOME["Home"]
        HomePresenter --- HomeView
    end

    subgraph CART["Cart / Checkout"]
        CartPresenter --- CartView
        CheckoutPresenter --- CheckoutView
    end

    subgraph EVENT["Event"]
        EventDetailPresenter --- EventDetailView
    end

    subgraph QUEUE["Queue"]
        QueuePresenter --- QueueView
    end

    subgraph MEMBER["Member Dashboard"]
        MemberDashboardPresenter --- MemberDashboardView
        OrderHistoryPresenter --- OrderHistoryView
        ProfilePresenter --- ProfileView
        RafflePresenter --- RaffleView
        ComplaintPresenter --- ComplaintView
        InquiryPresenter --- InquiryView
    end

    subgraph COMPANY["Company Dashboard"]
        CompanyDashboardPresenter --- CompanyDashboardView
        CompanyOrderHistoryPresenter --- CompanyOrderHistoryView
        CompanyPoliciesPresenter --- CompanyPoliciesView
        CompanyRafflePresenter --- CompanyRaffleView
        EventManagementPresenter --- EventManagementView
        EventPoliciesPresenter --- EventPoliciesView
        PolicyManagementPresenter --- PolicyManagementView
        SalesReportPresenter --- SalesReportView
        StaffManagementPresenter --- StaffManagementView
        CompanyInquiriesPresenter --- CompanyInquiriesView
    end

    subgraph ADMIN["Admin Dashboard"]
        AdminDashboardPresenter --- AdminDashboardView
        AdminAnalyticsPresenter --- AdminAnalyticsView
        AdminQueuePresenter --- AdminQueueView
        AdminUserManagementPresenter --- AdminUserManagementView
        AdminComplaintsPresenter --- AdminComplaintsView
    end
```

## Service Dependencies per Feature Area

| Feature Area | Application Services Used |
|---|---|
| Auth | `UserService` |
| Home | `EventService` |
| Cart | `OrderService` |
| Checkout | `OrderService`, `UserService` |
| Event Detail | `EventService`, `OrderService`, `QueueService`, `RaffleService` |
| Queue | `QueueService` |
| Member | `UserService`, `OrderService`, `RaffleService`, `ComplaintService`, `InquiryService` |
| Company | `CompanyService`, `EventService`, `OrderService`, `RaffleService`, `InquiryService` |
| Admin | `AdminService`, `UserService`, `SystemService`, `QueueService`, `ComplaintService` |

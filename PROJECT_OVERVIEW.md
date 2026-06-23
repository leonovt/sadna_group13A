# Project Overview — Sadna Group 13A

## High-Level Description

This is an **event ticketing platform** — a simplified version of Ticketmaster. Production companies (like concert promoters) create and manage events, and registered users can browse events, buy tickets, and track their order history.

The project follows **Domain-Driven Design (DDD)** with a clean 4-layer architecture:

```
Presentation  →  Application  →  Domain  →  Infrastructure
  (Vaadin UI)    (Services)   (Business Logic)  (Repos, Auth, Payment)
```

---

## Architecture Layers

### Layer 1 — Domain (the core)
The heart of the system. Contains all business rules, split into **Aggregates** — self-contained clusters of related objects each with a single root:

| Aggregate | What it models |
|---|---|
| `User` (Member / Guest) | People using the platform. Members can buy; Guests can browse. |
| `ProductionCompany` | A company that organizes events. Has a staff hierarchy (Founder → Owner → Manager) with fine-grained permissions. |
| `Event` | A show/concert with a venue map, zones, and seats. Supports 3 sale modes: regular, queue-based, or raffle. |
| `ActiveOrder` | A shopping cart in progress — items held but not yet paid. |
| `OrderHistory` | A completed purchase receipt, stored permanently. |
| `Raffle` | A lottery for high-demand events — users register, winners are drawn. |
| `TicketQueue` | A virtual queue system — users wait in line for tickets. |

The domain defines **interfaces** for repositories (`IEventRepository`, `ICompanyRepository`, etc.) so that the domain never depends on how data is stored.

---

### Layer 2 — Application (orchestration)
Services like `CompanyService`, `EventService`, `OrderService` sit here. They:
1. Validate the auth token
2. Load aggregates from repositories
3. Call domain logic
4. Save back and return a `Result<T>` (either success with data, or failure with an error message)

**Event Listeners** react to domain events — e.g. when an order completes, `NotificationEventListener` fires a notification to the user.

---

### Layer 3 — Infrastructure (adapters)
Concrete implementations of all interfaces the domain defined — in-memory repository implementations, a JWT-based `AuthImpl`, a `StubPaymentGateway` (fake payments for now), and a `PlatformBootstrap` that seeds initial data on startup.

---

### Layer 4 — Presentation (Vaadin UI)
A web UI built with **Vaadin** (a Java framework that renders server-side UI components). Each screen is a **View + Presenter pair**:
- The **View** is the visual layout — buttons, grids, text fields
- The **Presenter** is the controller — holds no UI state, just calls the service and tells the View what to display

URL routing is handled by Vaadin's `@Route` annotation.

---

## Issues Implemented

All 5 issues are in the **presentation layer** — the company management portal. The backend (services, domain) was already fully built. The task was to implement the UI that exposes that backend to company staff.

---

### Issue #38 — `CompanyDashboardView` + `CompanyDashboardPresenter`
**Route:** `company/:companyId`

The main landing page for a company. Shows the company name, status (active/suspended), and description — loaded via `CompanyService.getCompany()`. Also serves as the navigation hub with buttons linking to all other company management screens (Staff, Events, Sales, Policies).

---

### Issue #39 — `EventManagementView` + `EventManagementPresenter`
**Route:** `company/:companyId/events`

Lets authorized company staff view, create, publish, and unpublish events. Displays a grid of all the company's events with their status (Draft/Published), date, location, and available tickets. A "Create Event" dialog lets staff fill in event details. Uses `EventService` — this issue also added a `getCompanyEvents()` method to the service.

---

### Issue #40 — `StaffManagementView` + `StaffManagementPresenter`
**Route:** `company/:companyId/staff`

The HR screen for a company. Shows the full staff hierarchy (who has what role) using `CompanyService.getRoleTree()`. Lets a Founder/Owner appoint new managers, fire existing staff, and resign from the company. The resign button is styled in red to signal it is a destructive action.

---

### Issue #41 — `PolicyManagementView` + `PolicyManagementPresenter`
**Route:** `company/:companyId/policy`

Controls what individual managers are allowed to do. Each `CompanyPermission` is a capability: `MANAGE_EVENTS`, `MANAGE_POLICIES`, `MANAGE_DISCOUNTS`, `VIEW_REPORTS`. This screen lets a Founder/Owner select a manager and grant or revoke any combination of those permissions via `CompanyService.updatePermissions()`. Also shows the current permissions of all staff at a glance.

---

### Issue #42 — `SalesReportView` + `SalesReportPresenter`
**Route:** `company/:companyId/sales`

A company owner or manager with `VIEW_REPORTS` permission can see a financial summary: total orders, total revenue, and a scrollable table of every individual purchase. The data comes from `CompanyService.generateSalesReport()`, which aggregates all `OrderHistory` records linked to the company.

---

## Summary

The backend was a fully working ticketing platform, and all 5 issues together completed the **company management portal** — the set of screens that production companies use to manage their staff, events, policies, and financials on the platform.

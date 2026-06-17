# Requirements — Event Ticketing System (Versions 1–3 + General)

> **Course:** BGU Software Engineering Workshop (סדנה להנדסת תוכנה) 2026.
> **Purpose of this file:** single source of truth for *what the system must do*, used by Claude Code to audit completeness and correctness before submission.
> **Scope for this group:** **No מילואים (reserve-duty) exemption → full scope. Every requirement below is mandatory.** (The exemption tiers in the assignment PDFs do not apply to us, so they are intentionally omitted.)

The system is built incrementally across three versions. **Models and implementation must stay consistent** at every stage. Use the requirement IDs below (e.g. `I.1`, `II.4.7`, `SL-6`) when reporting traceability.

---

## 1. System overview

A ticketing platform connecting event producers and buyers. It is composed of **production companies** (event organizers), each holding identifying details and ticket inventory for events (shows, festivals, conferences), where every event/ticket has attributes (date, location, zone, seated/standing).

**Users** hold context-dependent **roles** (a user may hold several at once): **Owner** (מפיק/בעלים), **Buyer** (רוכש), **Manager** (מנהל), and platform **System Admin**. A registered user is a **Member** (מנוי); a visitor who has not identified is a **Guest** (אורח). Every visitor is also a buyer.

Glossary of producer roles:
- **Founder (מייסד):** the member who created the company. Has no appointer; only the founder may suspend/close or reopen the company.
- **Owner (בעל חברה):** appointed by the founder or another owner; full policy/management rights. The owner-appointment graph is a non-cyclic tree (each owner has at most one appointer).
- **Manager (מנהל):** appointed by an owner for operational management, with an explicitly chosen subset of permissions.

The platform calls **external services** for focused operations: **payment/credit clearing** and **ticket supply** (e.g. secure barcode/QR issuance). It also provides **real-time notifications** and must meet security & usability requirements.

---

## 2. Correctness constraints (אילוצי נכונות) — invariants that must hold after every operation

- A member has a **unique** identifying name.
- The platform has **at least one System Admin**; an Admin must be a Member.
- An Owner or Manager must be a Member.
- Platform actions are performed only by users **currently visiting** the platform.
- An **active (non-closed) company has at least one Owner**.
- A company (and its events) must have defined: purchase & discount **processes**, **defaults** for purchase mode and discount type, **purchase & discount policies**, and default rules (e.g. "no purchase/discount limits").
- A buyer has at most **one active order per event** at any moment.
- Adding tickets to an active order **locks/reserves** them for a bounded, preconfigured time (e.g. 10 min).
- If the purchase (incl. payment) is not fully completed before the timer expires, the order is **auto-cancelled** and tickets are **released** back to available inventory.
- A buyer's active order is **exclusive to them** and not modifiable by any other user while it is valid.
- A buyer may purchase **at most** the quantity of seats/tickets currently available for that event.

**External-connection constraints:** at least one payment-clearing service connection; at least one ticket-supply service connection.

**Integrity rules (כללי יושרה):**
- Charge buyers **only for executed transactions** and **only the declared amounts**.
- A company is paid **only** as the result of a **successful** purchase.
- A purchase succeeds **only if both** the ticket-issuance service **and** the payment service approve. The system must handle edge cases — e.g. if payment was charged but issuance failed (out of stock / network), the system must automatically **Refund** the payment and notify the buyer. (Also applies on event cancellation.)
- Compliance with applicable law (issuing receipts, anti-scalping, no ticket sales to under-18 for adult events).

---

## 3. Functional requirements

### I. System

- **I.1 — Platform initialization.** Bring the system up to a valid state satisfying all integrity rules and correctness constraints (active connections to payment + ticket-issuance services; ≥1 System Admin defined).
  - **(V3 update — config-file init)** Initialization is driven by one or more **external configuration files** (format of your choice) defining init parameters. Example: how many users may simultaneously hold tickets for an event before being queued. **DB connection details must come from a config file — never hard-coded.** The system must **fail to start** if initialization is invalid.
  - **(V3 update — initial-state file)** Optional initialization to a `state` defined in a **separate** external file. After config-file init, the system reads the initial-state file and runs the described **series of use-case stories** (with arguments) so that, when finished, the system is in the required state. Initialization runs **against the application layer** and may perform only **legal** operations. **If any operation fails, the whole initial-state init fails and reports an error.**
- **I.2 — Open marketplace (שוק).** Open a valid marketplace satisfying all integrity rules (active payment + supply connections; an Admin exists).
- **I.3 — Clearing, charging & refund.** Call a payment service recognized by the platform; pass transaction details; receive approval that payment/refund succeeded. Must support **more than one** payment service. Must support automatic **Refund** on issuance failure or event cancellation.
- **I.4 — Ticket issuance & registration.** Call a supply service recognized by the marketplace; pass package + customer details; receive supply approval. Must support **more than one** supply service.
- **I.5 — Real-time notifications.** Deliver real-time notifications when user attention is required, e.g.:
  - Producers/managers: event becomes **Sold Out**; their company is closed/frozen; their management roles change.
  - Buyers: purchase completed; event they bought a ticket for is **cancelled/rescheduled**; their **active-order timer is about to expire**.
  - All members: a new message/inquiry from an Admin or producer.
- **I.6 — Deferred notifications.** Notifications for members (buyers or producers) **offline** at creation time are stored and shown **immediately on their next login** (e.g. a refund processed while disconnected).
- **I.7 — Load management & virtual queue.** Handle extreme visitor load, especially at "box-office opening" for in-demand events. When concurrent **reservation** attempts cross a configured load threshold (system-wide or per-event), extra visitors are sent to a **waiting room (queue)** instead of directly to inventory. The system **releases waiters in batches** at a serveable rate and updates waiters in real time when it is their turn.

### II. Users

**II.1 — Guest visitor actions**
- II.1.1 Enter (visit) the platform → defined as a Guest. May search/view event info; the first ticket selection opens a time-limited **active order**, making the guest a buyer.
- II.1.2 Exit. The active order is bound exclusively to the current continuous visit; a guest who leaves **loses** access to it (tickets released at timer end) and cannot return to it.
- II.1.3 Register (unique identifying details) → stored as a **Member**. To gain member permissions in the same visit, must **Login**.
- II.1.4 Login (identified entry) → identified as a Member visitor.

**II.2 — Guest buying actions**
- II.2.1 Get event info (active companies + their events).
- II.2.2 View current inventory **and the event map**: structure of the venue incl. stage, entrances, zones. Unmarked zones show aggregate availability; **marked-seating zones show each seat visually (free/taken)**.
- II.2.3 Search events & tickets:
  - (a) **Global** search by event name, artist, category (live show, play, festival, conference) or keywords; filter by price range, date range, location/geo-area, and event/company rating.
  - (b) Search **within a specific company's** catalog; same filters (excluding company rating).
- II.2.4 **Reserve tickets** — add tickets to the buyer's active order; locks the selected tickets/seats for the configured time, temporarily blocking other users.
- II.2.5 Select tickets two ways: (a) **marked seats** (e.g. row 4, seat 12); (b) **quantity in an unmarked/standing zone**. Adding to the active order locks the specific seats or decrements zone availability for the configured time.
- II.2.6 **Wait in queue** — on abnormal load for an event, the visitor is queued (not sent straight to selection); shown queue status (estimated time / position if possible); when their turn comes they get time-limited access to the map & selection; inaction within the window expires their access and removes them.
- II.2.7 Manage the active order — view contents and make changes (remove tickets / change quantity if inventory allows, while the order is valid).
- II.2.8 **Checkout** — purchase the tickets in the active order per the company's purchase & discount policy (e.g. max-tickets-per-buyer), provided the timer has not expired. If the full set cannot be purchased (clearing error, external supply refusal, timer expiry), **no partial purchase occurs ("all or nothing")** and tickets are released.

**II.3 — Member buying actions** (everything a guest can do, plus the below; logout differs — see II.3.1)
- *Logout/exit difference:* a member's active order is bound to their **identity**; if they disconnect they can resume the purchase **from where they left off** if they Login again **before** the reservation timer expires. After the timer (e.g. 10 min) the order is cancelled and tickets released regardless.
- II.3.1 Logout → revert to Guest.
- II.3.2 Open a production company → become its **Founder**/first Owner.
- II.3.3 Submit a complaint to Admins (suspected integrity violation — scalping, fraud, unmet purchase terms).
- II.3.4 Get & change personal identifying info.
- II.3.5 View personal purchase history (future-event ticket status + past purchases). **History/receipts must be immune to later platform changes** (event cancellation, retroactive location/price change, company closure).
- II.3.6 **Register for a purchase-right raffle** — for in-demand events with a raffle mechanism; only members drawn receive a notification + a time-limited **authorization code** to enter reservation at sale opening.
- II.3.7 Send an inquiry to a production company.

**II.4 — Member as Owner / Founder**
- II.4.1 Manage inventory & events — add/edit/remove events; define dates/shows; manage ticket inventory (seating zones, marked rows/seats, standing-zone quantities).
- II.4.2 **Define venue configuration & event map** — graphical representation with stage, entrances, zones; each visual zone maps to a logical pricing/seating zone (standing-with-capacity, or seated with blocks/rows/seats); the map must accurately reflect inventory structure and live status (free/taken).
- II.4.3 **Change company purchase & discount types/rules (policies)** — define/change sale modes (regular sale or members' purchase-right raffle), purchase policy and discounts for the company's events (e.g. max tickets per buyer, early-bird discounts, age limits). *(Deferred in V1; required from V2 — see Appendix A.)*
- II.4.4 Receive & respond to inquiries from buyers.
- II.4.5 View purchase/order history — all tickets sold for the company's events. **Immune to later platform changes** (as II.3.5).
- II.4.6 Produce a **Sales Report** — total revenue & tickets sold; includes only events managed directly by this producer **plus all events managed by their appointment sub-tree** (owners/managers they appointed, transitively).
- II.4.7 **Appoint a Manager** — appoint a platform member (not already a manager/owner in that company) as a manager; the appointer chooses which permissions are granted from: manage event inventory, define venue config & event map, change purchase/discount policy types/rules, receive & respond to inquiries, view purchase/order history, produce sales report. The appointee must **accept** the appointment.
- II.4.8 **Appoint an Owner** — appoint a member (not already an owner in that company) as an additional Owner with identical management rights; the appointee must **accept (or reject)**. A new owner has at most one appointer; the founder has none; an already-appointed owner cannot be re-appointed by another; the owner-appointment structure is a non-cyclic tree.
- II.4.9 **Remove an Owner appointment** — an owner may remove an owner they previously appointed.
- II.4.10 **Renounce ownership** — a non-founder owner may voluntarily give up the role.
- II.4.11 **Change a Manager's permissions** — set/modify management options per manager (each manager has separate granular permissions).
- II.4.12 **Remove a Manager appointment** — remove a manager previously appointed.
- II.4.13 **Suspend/close company** — **founder only**. A closed company becomes inactive: non-owners/non-admins cannot get info on it; owners/managers are notified but their appointment tree is **not** deleted. *Constraint:* events/tickets of an inactive company do **not** appear in global search and cannot be purchased.
- II.4.14 **Reopen a closed company** — founder only; owners/managers notified of reactivation.
- II.4.15 **Request roles & permissions info** — the owner-roles tree and each manager's permissions.

**II.5 — Member as company Manager**
- May perform company management actions **according to the permissions** granted by the appointing owner.

**II.6 — Member as System Admin**
- II.6.1 Close a company (e.g. ToS violation, mass cancellations, fraud) → notifies owners/managers and cancels their appointments.
- II.6.2 Cancel/remove a member account (e.g. scalping, bots, ToS breach) → automatically revokes all roles/permissions that member held across companies.
- II.6.3 Handle complaints & respond (fake tickets, fictitious events, faults); send proactive system messages to producers and buyers.
- II.6.4 View **global** purchase history, by buyer or by company/event.
- II.6.5 View **Analytics** — live & historical metrics: visitor entry/exit rate, new-member registration rate, load metrics like reservation/purchase rate (critical during box-office openings).
- II.6.6 View & control **queues** in real time — manually increase/decrease the intake rate or "clear" a queue on technical fault.
- II.6.7 **Suspend a user** *(V2)* — for a bounded period or permanently; a suspended user may perform **view-only** actions.
- II.6.8 **Unsuspend a user** *(V2)* — cancel a temporary/permanent suspension.
- II.6.9 **View suspensions** *(V2)* — list suspensions with start date, duration, and end date.

---

## 4. Service-level (non-functional) requirements (SL)

- **SL-1 Consistency & concurrency.** Integrity rules hold for the entire uptime; init produces a rule-abiding system; consistency is never violated after any operation. **Special emphasis on race conditions:** absolute atomicity of reservation & purchase; hermetically prevent the same seat/ticket from being sold to two different buyers.
- **SL-2 Privacy & security.** Unique member identifiers are not exposed and not recoverable; user privacy ensured; data secured. *(V1: passwords stored **hashed**; secure **token-based** mechanism after login. V2: passwords never shown in cleartext to the user — e.g. masked with `*`.)*
- **SL-3 User experience.** Good UX to retain users. Depends on: response time (context-dependent quantitative targets that must hold even under load); help handling common errors; convenient UI; **a role-appropriate UI (SL-3.4)** — Guest, Member-visitor, Owner/Manager, Admin — exposing only the actions the visitor is permitted by role & permissions; **(SL-3.5, V2)** the UI **explains action outcomes** — successes and failures (e.g. on a reservation blocked by an age policy, show a message like "Tickets for this event can be purchased from age 18 and up"), and confirms when user-initiated actions succeed.
- **SL-4 Load, stress & availability.** Subject to resources, support: unlimited concurrent visitors; unlimited companies/events/ticket-types; extreme & sudden traffic spikes ("box-office opening") while staying functional; continuous availability/accessibility (except intentional shutdown), e.g. during maintenance/service swaps.
- **SL-5 Robustness.** Resilient to edge cases — demanding load, broken communication, disconnection from external services (e.g. credit clearing). *(V3 detail: handle comm loss between components and with external systems; be resilient to external misbehavior — request-send failure, incompatible interface, no response.)*
- **SL-6 Recovery.** Return to normal operation after faults/edge cases. *(V3 detail: e.g. if the DB link drops, the system auto-resumes normal operation after the link is restored — no manual restart.)*
- **SL-7 Persistency.** System state is recoverable after shutdown (intentional or not). *(V3: state held in an **external** DB, separating compute from data; **must support a remote DB**, not on the same machine as the app.)*
- **SL-8 Monitoring.** Track operations and faults. *(V1: maintain an **event log** and an **error log**, always viewable. The event log records platform calls — which use cases ran, with which parameters. The error log records system errors. **Negative scenarios are NOT errors.** Prefer real-time error tracking enabling audits/investigations.)*

---

## 5. Version deltas (what each version added)

### Version 1 — Initial implementation
- Build **domain**, **service/application**, and **infrastructure** layers; two-layer architecture per the architecture model. In V1 the system is operated **via tests only**.
- Service-level implemented: **SL-1** (consistency + race conditions), **SL-2** (hashed passwords + token-based auth), **SL-4** (many concurrent users + accessible), **SL-8** (event log + error log).
- Functional: System `I.1, I.2, I.3, I.4, I.7`; in V1 **no real external connections** — use a **proxy** to interface with the domain. Users: all V0 functional reqs **except `II.4.3`** (deferred); **no** real-time/deferred notifications yet. (Representations of deferred requirements must remain in the models.)
- Implementation guidance: enforce correctness constraints via **structure & operations, not tests**; add **interfaces** where implementation may change (incl. for entity collections that already have IDs); careful locking must not harm availability.
- **GitHub discipline:** version code on GitHub; tasks as **issues**, each tied to the current **milestone**; each task gets a **branch + PR** labeled `feature`/`bug`; each dev task must include its relevant tests (otherwise the task is incomplete); the final commit before submission is tagged with the version tag.
- **CI:** GitHub Actions running **all** tests for all version contents; CI runs as a step **before push to main**.
- **Tests:** acceptance tests reach the system **through the application layer only**; use **Mocks** (a mocking framework) where appropriate; include **negative tests** (where failing is success) and **concurrency tests**. Concurrency tests must spin up multiple threads released from a single starting gate (e.g. `ExecutorService` + `CountDownLatch`) to hammer the sensitive function simultaneously, then assert system state is valid. Tests must pass **regardless of optimistic vs pessimistic locking**.

### Version 2 — Representation, notifications & policy definitions
- Service-level: a **browser-adapted UI**; **SL-2** passwords not shown in cleartext; **SL-3.4** role-specific UI exposing only permitted actions; **NEW SL-3.5** UI explains success/failure of actions. Add a **communication component** that receives **HTTP** requests and maps them to application-layer functions.
- Functional: **`II.4.3`** define/edit purchase & discount policy types and rules (see Appendix A); **NEW** admin actions **`II.6.7` suspend user**, **`II.6.8` unsuspend**, **`II.6.9` view suspensions**; **`I.4` & `I.5`** real-time + deferred notifications.
- Tests: **automatic code-coverage report** while running (e.g. **JaCoCo**); tests for all reqs incl. real-time + deferred notifications.
- Implementation: use a **Web Application Framework** (e.g. **Spring Boot**) for communication; GUI as a **Web client**; UI receives & displays real-time notifications **(not polling)**; UI supports defining/editing discount & purchase types/rules and their **compositions**; write tests for **Domain + Application only** (not the UI).
- Architecture: client / **communication (REST API)** / application / infrastructure (shared cross-component info) / updated domain.

### Version 3 — Persistence, robustness & crash recovery
- Service-level: **SL-7 persistence** (state in external DB; compute/data separation; **support a remote DB**); **SL-5 robustness** (comm loss + external misbehavior); **SL-6 recovery** (auto-resume after fault, e.g. DB reconnection).
- Functional: **`I.1` updated** — (i) **config-file init** (params + DB connection in config, not hard-coded; fail-fast on invalid config); (ii) **initial-state file** init (run a series of use-case stories so the system reaches a defined state; runs via the application layer; only legal ops; any failure fails the whole init with an error). **`I.3` & `I.4`** — connect to the **real external payment + ticket-issuance systems (WSEP)** from the accompanying doc.
- **Tests:** **robustness** tests (vs external systems + DB); **valid-init** tests (the system **does not come up if init is invalid**); a **dedicated test config** so tests do **not** hit the real external systems and do **not** pollute the real DB.
- **Models:** update the **class model**; update the **use-case scenarios** for initialization.
- **Architecture (current):** client / communication / application / infrastructure / domain / DB. **The ORM is the Data Access Layer — do not draw a separate DAL.**

---

## Appendix A — Discount & purchase policies (V2 detail)

**Discount policy**
- Discounts are **multiplicative, in percent**; can target a **company** or a specific **event** (e.g. "10% off all events of company X"; "20% off 'Rick & Morty: The Musical' on 20/6/26 20:00").
- **Discount types:** (a) **simple** (price calc only); (b) **conditional** — carries a predicate (e.g. "10% off when buying ≥2 tickets"; "15% off for purchases until 15/5/26") — support **min/max ticket** and **time-range** conditions, with a design general enough to add new conditions easily; (c) **coupon** — % discount + coupon **code** (entered at checkout) + validity.
- **Composition** when several discounts apply: **max** ("no stacking" — apply the largest) or **sum** ("stacking" — accumulate, subtract from price).

**Purchase policy**
- A **predicate** deciding whether a user may purchase; definable at **company** or **event** level.
- **Rules (this stage):** limits by **age** or by **ticket quantity** (min/max); design general enough to add new conditions easily.
- **Composition** via logical **and**/**or** to **arbitrary depth** (e.g. "age ≥18 AND [at most 2 tickets OR at least 100 tickets]").
- Implementation: use the **Composite** design pattern (composition classes per type and per operator).

## Appendix B — V3 persistence implementation emphases

- Use an **ORM** as the Data Access Layer separating core from DB; map ORM entity names to the **existing** core implementation. **Do not** design an independent DB schema; the link is layered (core → data).
- **Do not persist inherently transient values/objects** (e.g. waiting queues).
- **Caching** via the ORM's built-in cache mechanism.
- **Transactions:** define **each use case as a separate transaction**.
- **External DB (optional GCP):** $50 credit; use the **cheapest** config (e.g. PostgreSQL/MySQL, `db-f1-micro`, HDD, ~10 GB, cost-effective region, backups & HA disabled). You may develop on a **local in-memory DB (H2)** and switch to cloud **only for deployment** — a **config-file change only, no code change**. Set a billing budget/alerts.
- **README** must explain how to initialize the system and define the config-file and initial-state-file formats.

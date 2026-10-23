# Clientra CMS

A full-stack **Customer Management System** for managing customer profiles, interactions, orders and support tickets — from the first enquiry (lead), through purchases, to after-sales support.

| Layer | Technology |
|---|---|
| Frontend | React 18 + TypeScript (Vite), Tailwind CSS ("Teal & Sand" design), React Router 6, TanStack Query, React Hook Form + Zod, Recharts |
| Backend | Java 21, Spring Boot 3.3 (Web, Data JPA, Security, Validation, Cache, Mail, Actuator), MapStruct, jjwt, OpenPDF, Commons CSV |
| Database | MySQL 8 (InnoDB, utf8mb4) with Flyway migrations |
| Cache | Redis (dashboard KPIs 60s, reports 5 min) — the app keeps working if Redis is down |
| Docs & tests | springdoc Swagger UI, JUnit 5, Mockito, Spring Boot Test + MockMvc, JaCoCo |

Everything runs locally — no Docker or cloud services needed.

---

## Features

- **Authentication & RBAC** — JWT access (15 min) + refresh (7 days) tokens with silent refresh, BCrypt passwords, forgot-password with an emailed 6-digit code. Roles: **Admin**, **Sales Executive**, **Support Agent**.
- **Customers** — search, filters (status, segment, owner, tag), pagination, duplicate email/phone detection (409 with link to the existing record), auto customer codes (`CUST-00218`), addresses, tags, soft delete, lifecycle status (Lead → Active → Inactive → Churned).
- **Customer 360°** — profile, contact details, account summary, merged timeline (calls, orders, tickets, tag/segment changes), orders/tickets/notes tabs, **churn-risk score** (RFM + open tickets).
- **Interactions & follow-ups** — log calls, emails, meetings, demos, quotes and notes with follow-up dates; follow-ups due today appear on the dashboard and are emailed/notified each morning.
- **Products & Orders** — catalogue with GST rates; orders with line items (GST included), payment status and fulfilment status. **Mark Paid is idempotent** and updates lifetime value + converts Lead → Active; refunds reverse it.
- **Support desk** — Kanban board (drag to change status), SLA by priority (High 8h / Medium 24h / Low 48h) with live countdown, auto-assignment to the least-loaded agent, conversation with replies (emailed to the customer), internal notes, escalation, SLA breach monitor every 5 minutes.
- **Dashboard** — KPIs, monthly revenue vs new customers, customers by segment, follow-ups due today, at-risk customers.
- **Reports & Analytics** — retention, lead→customer conversion, ticket resolution time, SLA compliance, lifecycle funnel, tickets by category, sales executive performance, **PDF export**.
- **Bulk operations** — CSV import with a per-row validation report, CSV export of the filtered list.
- **Notifications** — in-app bell (follow-ups, ticket assignment, SLA breaches, payments, churn risk) with optional email.
- **Administration** — users & roles, audit-log viewer (every create/update/delete), system tools.

---

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java JDK | 21 | `java -version` |
| Node.js | 18+ (tested on 24) | `node -v` |
| MySQL Server | 8.x, running on `localhost:3306` | Windows service `MySQL80` |
| Redis | any recent, on `localhost:6379` (optional) | Windows service `Redis` |

Maven is **not** required — the project ships with the Maven Wrapper (`mvnw` / `mvnw.cmd`).

---

## 1. Configure secrets (never committed)

```bash
cd backend/src/main/resources
copy application-local.yml.example application-local.yml
```

Open `application-local.yml` and fill in:

| Key | Value |
|---|---|
| `spring.datasource.username` / `password` | A MySQL user that can create the `clientra_db` and `clientra_test` databases (e.g. `root`). Wrap the password in single quotes if it contains `# : @ ! %` etc. |
| `app.jwt.secret` | Any random string of 64+ characters |
| `spring.mail.*`, `app.mail.*` | Optional — see **Email setup** below |

`application-local.yml` and `frontend/.env` are git-ignored. The frontend only needs:

```bash
copy frontend\.env.example frontend\.env
```

(`VITE_API_URL=http://localhost:8081` — no secrets.)

> **Prefer a dedicated MySQL user?** Run this in MySQL Workbench as root and put `clientra` / your password in `application-local.yml`:
> ```sql
> CREATE USER 'clientra'@'localhost' IDENTIFIED BY 'choose-a-password';
> GRANT ALL PRIVILEGES ON clientra_db.* TO 'clientra'@'localhost';
> GRANT ALL PRIVILEGES ON clientra_test.* TO 'clientra'@'localhost';
> ```

### Email setup (Gmail SMTP)

Email is used for password-reset codes, welcome emails, ticket replies/resolution, follow-up reminders and SLA alerts. Without it, emails are written to the backend log instead.

1. Use a Gmail account with **2-Step Verification** turned on (Google Account → Security → 2-Step Verification).
2. Go to **Google Account → Security → App passwords** (or <https://myaccount.google.com/apppasswords>).
3. Create an app password named `Clientra` and copy the 16-character code.
4. In `application-local.yml`:
   ```yaml
   spring:
     mail:
       username: your.address@gmail.com
       password: 'abcdefghijklmnop'   # the 16-character app password, no spaces
   app:
     mail:
       enabled: true
       from: "Clientra CMS <your.address@gmail.com>"
   ```
5. Restart the backend. **Settings → System** shows whether email is enabled.

Other providers work the same way — change `spring.mail.host` / `port` in `application.yml` (e.g. Outlook: `smtp.office365.com`, port 587).

---

## 2. Run

**Backend** (port **8081**):

```bash
cd backend
mvnw.cmd spring-boot:run
```

On first start Flyway creates the schema (`clientra_db`, 13 tables) and the seeder loads demo data (6 users, 50 customers, ~130 orders, 23 tickets).

**Frontend** (port **5173**):

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>.

| URL | What |
|---|---|
| http://localhost:5173 | Web app |
| http://localhost:8081/swagger-ui.html | API documentation (Swagger UI) |
| http://localhost:8081/actuator/health | Health (DB + Redis) |

### Demo logins (password for all: `Clientra@123`)

| Email | Role | Notes |
|---|---|---|
| `neha.sharma@clientra.in` | Admin (Sales Manager) | Sees everything; owns Arvind Rao, Nexa Logistics |
| `admin@clientra.in` | Admin | |
| `karan.mehta@clientra.in` | Sales Executive | Customers, interactions, orders, products (read-only) |
| `arjun.nair@clientra.in` | Sales Executive | |
| `ritu.desai@clientra.in` | Support Agent | Ticket board, read-only customers |
| `sana.qureshi@clientra.in` | Support Agent | |

### Reset demo data

Drops all tables, re-runs migrations and reseeds:

```bash
cd backend
mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--app.reset-db=true --spring.flyway.clean-disabled=false"
```

---

## 3. Tests

```bash
cd backend
mvnw.cmd verify
```

- **33 unit tests** (JUnit 5 + Mockito): customer creation & duplicate detection, order totals/GST, idempotent payment & LTV, refunds, ticket SLA & auto-assignment, breach monitor, churn scoring, JWT, formatting.
- **24 integration tests** (Spring Boot Test + MockMvc) against a separate local schema `clientra_test` (recreated on each run, every test rolls back): login/refresh/401, customer CRUD + 409 + RBAC 403, Customer 360, ticket board/assign, order → paid → LTV, dashboard, reports.
- Coverage report: `backend/target/site/jacoco/index.html`.

Frontend checks:

```bash
cd frontend
npm run lint
npm run build
```

---

## Project structure

```
clientra-cms/
├─ backend/                         Spring Boot API (package io.clientra)
│  ├─ src/main/java/io/clientra/
│  │  ├─ config/        security, cache, OpenAPI, clock, demo seeder
│  │  ├─ controller/    REST controllers
│  │  ├─ dto/           request/response records (Bean Validation)
│  │  ├─ entity/        JPA entities + enums
│  │  ├─ exception/     uniform JSON errors (GlobalExceptionHandler)
│  │  ├─ mapper/        MapStruct mappers
│  │  ├─ repository/    Spring Data repositories + Specifications
│  │  ├─ scheduler/     SLA monitor, follow-up reminders, start-up jobs
│  │  ├─ security/      JWT service + filter, current user
│  │  ├─ service/       business logic
│  │  └─ util/          INR / phone formatting
│  └─ src/main/resources/db/migration   Flyway V1–V6
└─ frontend/                        React + TypeScript app
   └─ src/
      ├─ components/    UI kit, layout (sidebar, header, search, notifications)
      ├─ lib/           API client (JWT + silent refresh), auth store, types, formatting
      └─ pages/         auth, dashboard, customers, interactions, orders, products, tickets, reports, settings
```

### Database (MySQL)

`users`, `customers`, `addresses`, `tags`, `customer_tags`, `interactions`, `products`, `orders`, `order_items`, `support_tickets`, `ticket_comments`, `activity_logs`, `notifications`, `password_reset_tokens` — normalized (3NF), InnoDB foreign keys, indexes for the common filters. Timestamps are stored in UTC and shown in the browser's time zone; business days (follow-ups, months) use `app.timezone` (Asia/Kolkata).

---

## API overview

All endpoints except login/refresh/forgot/reset require `Authorization: Bearer <accessToken>`. Errors use one JSON shape: `{status, error, message, fieldErrors?, existingId?}`.

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` · `/api/auth/refresh` | Public | Access + refresh tokens |
| POST | `/api/auth/forgot-password` · `/api/auth/reset-password` | Public | Emailed 6-digit code |
| GET/PUT | `/api/auth/me` · POST `/api/auth/change-password` | All | Own profile |
| GET | `/api/customers` | All | Search, filter, paginate |
| GET | `/api/customers/{id}` | All | Customer 360 |
| POST · PUT · PATCH `/status` | `/api/customers[/{id}]` | Admin, Sales | Create (409 on duplicate) / update / lifecycle |
| DELETE | `/api/customers/{id}` | Admin | Soft delete |
| GET · POST | `/api/customers/export` · `/api/customers/import` | All · Admin, Sales | CSV export / import with report |
| GET · POST | `/api/interactions` · `/api/interactions/follow-ups` | Admin, Sales | Log activity, follow-ups |
| GET · POST · PUT | `/api/products` | Read: Admin, Sales · Write: Admin | Catalogue |
| GET · POST | `/api/orders` · `/api/orders/kpis` | Admin, Sales | Orders with items |
| PATCH | `/api/orders/{id}/payment` · `/status` | Admin, Sales | Mark paid (LTV) / refund / ship / deliver |
| GET · POST | `/api/tickets` · `/api/tickets/{id}` | Admin, Support | Board / raise / detail |
| PATCH | `/api/tickets/{id}/assign` | Admin | Assign agent |
| PATCH · POST | `/api/tickets/{id}/status` · `/comments` · `/escalate` | Admin, Support | Workflow, replies, notes |
| GET | `/api/dashboard/kpis` · `/api/dashboard/follow-ups` | All | Cached KPIs |
| GET | `/api/reports/summary` · `/api/reports/summary.pdf` | Admin | Analytics + PDF |
| GET | `/api/notifications` · `/unread-count` | All | In-app notifications |
| GET · POST | `/api/users` · `/api/audit-logs` · `/api/admin/*` | Admin | Users, audit trail, maintenance |
| GET | `/api/search?q=` | All | Header search (customers + tickets) |

Full, interactive documentation: **Swagger UI**.

# Cloud-Native POS System – DevOps Project

This project demonstrates a production-style Point-of-Sale (POS) system.

**Technologies:**
- Spring Boot (POS API)
- Docker & Kubernetes
- Terraform (Infrastructure as Code)
- CI/CD with GitHub Actions
- Secrets management, monitoring, automated rollbacks

**Focus:** Infrastructure, deployment, and DevOps workflow, not payment processing or frontend polish.

## Database schema

The database schema is managed by [Flyway](https://flywaydb.org). Versioned
migrations live in `app/src/main/resources/db/migration` (currently `V1`
through `V19`, e.g. `V1__create_product_table.sql`,
`V4__add_orders_and_order_lines.sql`, `V17__add_pos_sessions.sql`,
`V18__add_order_store_employee.sql`, `V19__make_order_attribution_non_nullable.sql`)
and are
applied automatically at application startup. Each product carries a caller-supplied, unique `sku` (the stable
identifier used by POS systems), an `active` flag (products are deactivated
rather than deleted), and an optional reference to a `category` (required,
unique name) via `category_id`. Hibernate runs with `ddl-auto=validate`, so it
verifies the schema against the JPA model instead of modifying it. Databases
created before Flyway was introduced are baselined automatically on first
startup (`spring.flyway.baseline-on-migrate=true`) and are not recreated.

## Local PostgreSQL (development)

Start the database:

    docker compose up -d

Wait until the `pos-postgres` container reports `healthy` (`docker compose ps`),
then start the application from `app/` with `mvnw spring-boot:run` or
`java -jar target/posapp-1.0.0.jar`. On startup Flyway applies the versioned
migrations and Hibernate validates the schema.

Credentials default to the local development values (`posdb`/`posuser`/
`pospassword` on `localhost:5432`) and can be overridden with `POSTGRES_*`
environment variables (Compose) or `SPRING_DATASOURCE_*` environment
variables (application). Data persists in the `pgdata` named volume:
`docker compose down` keeps it, `docker compose down -v` deletes it.

Quick schema verification:

    docker compose exec postgres psql -U posuser -d posdb \
      -c "SELECT installed_rank, version, description, success FROM flyway_schema_history;" \
      -c "SELECT column_name, data_type, numeric_precision, numeric_scale FROM information_schema.columns WHERE table_name = 'product' ORDER BY ordinal_position;"

## POS PIN authentication & sessions

Employees authenticate at a store with the PIN managed through the employee
PIN endpoints and receive a temporary, store-scoped bearer session
(`pos_session`, Flyway V17). Only active employees assigned to an active
store, holding a PIN that verifies against their stored credential, are
accepted.

Endpoints (all under `/api/v1`):

| Method | Path | Auth | Purpose |
| ------ | ---- | ---- | ------- |
| POST | `/pos/authenticate` | PIN in body | Exchange `{ "storeId": 123, "pin": "0421" }` for a session. Returns `200` with `{ token, employeeId, employeeName, storeId, storeName, expiresAt }`. |
| GET | `/pos/session` | `Authorization: Bearer <token>` | Return the current session's non-sensitive employee/store context. `401` for a missing, malformed, unknown, expired, or revoked token. |
| POST | `/pos/logout` | `Authorization: Bearer <token>` | Revoke the current session. `204`; idempotent, so repeated logout is safe. |
| POST | `/pos/orders` | `Authorization: Bearer <token>` | Create an order from `{ diningOption, lines: [{ productId, quantity }] }`. Returns `201` with the order plus `storeId`/`employeeId` **derived from the session** (never supplied by the client). |
| GET | `/pos/orders/{id}` | `Authorization: Bearer <token>` | Return the order, only if it belongs to the session's store. Missing and other-store orders both return the same `404`. |
| PATCH | `/pos/orders/{id}/status` | `Authorization: Bearer <token>` | Transition the order's status, only if it belongs to the session's store. Same `404` scoping; existing transition rules apply. |

Security notes:

- The **raw bearer token is returned only once**, by the authentication
  response. Only its SHA-256 hash is stored (`pos_session.token_hash`,
  unique). The token is a cryptographically random value, never derived from
  the PIN, and never logged.
- The PIN is treated as a string and used verbatim (leading zeros preserved);
  it is never stored, logged, or returned. Authentication failures are
  **generic** (`401 "Invalid store or PIN."`) and do not reveal whether the
  store, employee, assignment, or credential exists.
- The POS order endpoints reuse `PosSessionService.currentSession` for the
  bearer check (no duplicated token logic) and confine retrieval/status updates
  to the session's store. Every order records the session's store and employee
  (`orders.store_id` / `orders.employee_id`, added nullable in Flyway V18 and
  made **required** in Flyway V19); the client cannot choose this attribution.
  The ownership check and the status update run in one transaction.
- The legacy unauthenticated `/api/v1/orders` endpoints have been **removed**:
  `/pos/orders` is the sole HTTP interface for creating, reading, and
  transitioning orders. The other management endpoints (employee, store, menu,
  payment) remain unauthenticated as before.

Configuration (see `app/src/main/resources/application.properties`, all
overridable via environment variables):

- `pos.session.ttl` (default `8h`) – bearer-session lifetime from issuance.
- `pos.auth.rate-limit.max-attempts` (default `5`), `.window` (default `5m`),
  `.cooldown` (default `15m`) – per `(store, client)` failed-attempt limit
  before a cooldown lockout (`429 Too Many Requests`).
- `pos.auth.rate-limit.trust-forwarded-for` (default `false`) – use the
  `X-Forwarded-For` header for the client identity. Enable **only** behind a
  trusted reverse proxy that overwrites it; otherwise clients could spoof the
  header to evade the limit. By default the remote address is used.
- `pos.auth.rate-limit.max-entries` (default `100000`) – bounds the in-memory
  bucket map so arbitrary client input cannot grow it without limit.

Limitations: the rate limiter is **process-local** (in-memory) and is not
shared across multiple application instances; a horizontally scaled
deployment would need a shared store (e.g. Redis) for a global limit. The
menu catalog is still global, so a POS session at one store may order any
active menu item — per-store menu scoping, verifying the PIN again on each
transaction, clock-in/out, and role/permission authorization are intentionally
out of scope.

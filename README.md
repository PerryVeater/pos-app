# POS System — Spring Boot backend

A point-of-sale backend: organizations and stores, employees with PIN
credentials, a menu catalog, POS sessions, orders and payments, on PostgreSQL
with a Flyway-managed schema.

**Implemented in this repository:**

- Spring Boot 3.5 on Java 17 (`app/`)
- PostgreSQL 16 through Spring Data JPA, with the schema owned by Flyway
  (`ddl-auto=validate` — Hibernate never creates or alters tables)
- Two separate authenticated surfaces: opaque PIN-exchanged POS sessions, and
  back-office endpoints guarded by OIDC access tokens from a Keycloak realm
  exported into the repository
- Testcontainers-backed integration tests against a real PostgreSQL container
  (and a real Keycloak container for the identity boundary)
- A Docker image (`Dockerfile`) and `docker-compose.yml` for local development
- GitHub Actions CI: build, test, and publish an image from `main` only

**Planned, deliberately not present yet:**

- Kubernetes manifests and Terraform infrastructure
- Prometheus/Grafana monitoring, alerting and dashboards
- Secret management and automated rollback

Earlier revisions of this file listed the last group as if it existed. It does
not; treat it as future work rather than as a description of the tree.

## Build and test

Requires JDK 17 or later with `JAVA_HOME` set, and a running Docker daemon —
the integration tests start PostgreSQL 16 through Testcontainers, and the
back-office security tests also start Keycloak 26 with the checked-in realm.
The first run pulls both images.

    cd app
    ./mvnw verify

The suite takes several minutes: most of the wall clock is container startup
and Spring context creation for the repository tests, not assertions.
`verify` is the gate to use before pushing. `-DskipTests package` builds the jar
without running anything and is not a substitute.

## Database schema

The database schema is managed by [Flyway](https://flywaydb.org). Versioned
migrations live in `app/src/main/resources/db/migration` (currently `V1`
through `V25`, e.g. `V1__create_product_table.sql`,
`V4__add_orders_and_order_lines.sql`, `V17__add_pos_sessions.sql`,
`V19__make_order_attribution_non_nullable.sql`,
`V20__add_pos_session_lifecycle_indexes.sql`,
`V21__add_pin_secret_anchor.sql`,
`V22__add_order_query_indexes.sql`,
`V23__add_optimistic_locking.sql`,
`V24__make_business_timestamps_instant.sql`,
`V25__link_employees_to_identities.sql`)
and are
applied automatically at application startup. Each product carries a caller-supplied, unique `sku` (the stable
identifier used by POS systems), an `active` flag (products are deactivated
rather than deleted), and an optional reference to a `category` (required,
unique name) via `category_id`. Hibernate runs with `ddl-auto=validate`, so it
verifies the schema against the JPA model instead of modifying it. Databases
created before Flyway was introduced are baselined automatically on first
startup (`spring.flyway.baseline-on-migrate=true`) and are not recreated.

Migration rules — forward-only, never edit an applied migration, and when a
data-destructive statement is acceptable — are in
[docs/migration-policy.md](docs/migration-policy.md).

## Local PostgreSQL (development)

Start the database and the identity provider:

    docker compose up -d

Wait until the `pos-postgres` and `pos-keycloak` containers report `healthy`
(`docker compose ps`), then start the application from `app/` with
`mvnw spring-boot:run` or `java -jar target/posapp-1.0.0.jar`. On startup Flyway
applies the versioned migrations and Hibernate validates the schema.

Credentials default to the local development values (`posdb`/`posuser`/
`pospassword` on `localhost:5432`) and can be overridden with `POSTGRES_*`
environment variables (Compose) or `SPRING_DATASOURCE_*` environment
variables (application). Data persists in the `pgdata` named volume:
`docker compose down` keeps it, `docker compose down -v` deletes it.

Keycloak serves the `pos` realm on `http://localhost:8180`, imported from
`keycloak/pos-realm.json` by the container's `--import-realm` flag; no manual
realm setup is needed. The export contains four development users (password
`dev-only-password`) whose differences are the cases the back-office rules
care about:

| User | Linked to an employee? | Carries `brand-admin`? |
| ---- | ---------------------- | ---------------------- |
| `denver.admin` | yes (once linked, see below) | yes |
| `auckland.admin` | yes (once linked) | yes |
| `unlinked.admin` | no | yes |
| `no-role.admin` | yes (once linked) | no |

A token for scripts comes from the confidential `pos-scripts` client, which
installs the audience mapper that addresses tokens to this API:

    curl -s -X POST http://localhost:8180/realms/pos/protocol/openid-connect/token \
      -d grant_type=password -d client_id=pos-scripts \
      -d client_secret=dev-only-pos-scripts-secret \
      -d username=denver.admin -d password=dev-only-password

The link between an account and an employee is a database fact, not a claim in
the token, so it has to be set before a realm user can use the back office:

    docker compose exec postgres psql -U posuser -d posdb \
      -c "UPDATE employee SET idp_subject = '11111111-1111-4111-8111-111111111111' \
          WHERE name = 'Denver Admin';"

The UUIDs in the export are fixed on purpose, so a subject identifies the same
account on every machine. The realm lives in the container's own filesystem
rather than in a volume: `docker compose down` discards it and the next `up`
re-imports the export. Anything changed in the admin console is therefore
temporary — `keycloak/pos-realm.json` is the realm's source of truth.

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

A session ends at its absolute expiry (`pos.session.ttl`, 8h by default), on
logout, and whenever the credential or entitlement behind it changes: a PIN
rotation or removal, the employee being deactivated, or their assignment to
that store being removed. An employee holds at most
`pos.session.max-per-employee` (3) live sessions - the oldest is revoked when
a new login would exceed it, so a terminal that never logged out cannot keep
one alive forever - and a background job (`PosSessionRetentionJob`) deletes
rows whose expiry is older than `pos.session.retention` (7 days).

Endpoints (all under `/api/v1`):

| Method | Path | Auth | Purpose |
| ------ | ---- | ---- | ------- |
| POST | `/pos/authenticate` | PIN in body | Exchange `{ "storeId": 123, "pin": "0421" }` for a session. Returns `200` with `{ token, employeeId, employeeName, storeId, storeName, expiresAt }`. |
| GET | `/pos/session` | `Authorization: Bearer <token>` | Return the current session's non-sensitive employee/store context. `401` for a missing, malformed, unknown, expired, or revoked token. |
| POST | `/pos/logout` | `Authorization: Bearer <token>` | Revoke the current session. `204`; idempotent, so repeated logout is safe. |
| POST | `/pos/orders` | `Authorization: Bearer <token>` | Create an order from `{ diningOption, lines: [{ productId, quantity }] }`. Returns `201` with the order plus `storeId`/`employeeId` **derived from the session** (never supplied by the client). |
| GET | `/pos/orders/{id}` | `Authorization: Bearer <token>` | Return the order, only if it belongs to the session's store. Missing and other-store orders both return the same `404`. |
| PATCH | `/pos/orders/{id}/status` | `Authorization: Bearer <token>` | Transition the order's status, only if it belongs to the session's store. Same `404` scoping; existing transition rules apply. |
| POST | `/pos/payments` | `Authorization: Bearer <token>` | Create a payment from `{ orderId, amount, method }` for an order owned by the session's store. Returns `201`. A missing or other-store order both return the same `404`. |
| GET | `/pos/payments/{id}` | `Authorization: Bearer <token>` | Return the payment, only if its order belongs to the session's store. Missing and other-store payments both return the same `404`. |
| PATCH | `/pos/payments/{id}/status` | `Authorization: Bearer <token>` | Transition the payment's status (`PENDING -> COMPLETED/FAILED`, `COMPLETED -> REFUNDED`), only if its order belongs to the session's store. Same `404` scoping. |

Security notes:

- The **raw bearer token is returned only once**, by the authentication
  response. Only its SHA-256 hash is stored (`pos_session.token_hash`,
  unique). The token is a cryptographically random value, never derived from
  the PIN, and never logged.
- The PIN is treated as a string and used verbatim (leading zeros preserved);
  it is never stored, logged, or returned. Authentication failures are
  **generic** (`401 "Invalid store or PIN."`) and do not reveal whether the
  store, employee, assignment, or credential exists.
- Repeated failures at a `(store, client)` pair lock that client out for a
  cooldown, and the moment a lockout begins is recorded once as a structured
  `WARN` line: `event=pos_auth_lockout storeId=… client=… storeFailures=…`,
  plus `identifiedEmployeeId`/`employeeFailures` when the submitted PIN
  matched a claim. A PIN that matches nobody is counted against the store
  alone, because the server has not identified anybody. The line never
  contains the PIN or the fingerprint derived from it.
- PIN fingerprints are HMAC-`pos.pin.hmac-secret`, so every store claim is
  bound to the secret that produced it. Startup compares the configured secret
  against a one-row anchor (`pos_pin_secret_anchor`, Flyway V21) and **refuses
  to boot** on a mismatch: the alternative is starting normally while every
  employee's login fails exactly like a wrong PIN. Because fingerprints cannot
  be recomputed from the stored PBKDF2 hashes, rotation is re-enrollment - arm
  `pos.pin.fingerprint-reset=true` for one startup to drop the claims and
  re-anchor, then have each employee set their PIN again.
- The POS order endpoints reuse `PosSessionService.currentSession` for the
  bearer check (no duplicated token logic) and confine retrieval/status updates
  to the session's store. Every order records the session's store and employee
  (`orders.store_id` / `orders.employee_id`, added nullable in Flyway V18 and
  made **required** in Flyway V19); the client cannot choose this attribution.
  The ownership check and the status update run in one transaction.
- The legacy unauthenticated `/api/v1/orders` endpoints have been **removed**:
  `/pos/orders` is the sole HTTP interface for creating, reading, and
  transitioning orders. Likewise the legacy `/api/v1/payments` endpoints have
  been **removed** and replaced by `/pos/payments`, the sole payment interface.
  POS payment ownership is enforced through the payment's order (a payment has no
  store column of its own): creation resolves the order with the same
  store-scoped lookup used for orders (a missing or foreign order is refused
  identically), and retrieval/status updates verify the order's store before
  reading or writing. Payment rules are unchanged (positive amount, no payment
  for a cancelled order, the `PENDING/COMPLETED/FAILED/REFUNDED` transition
  graph); no order-total matching or duplicate-payment restriction is added.
- The management endpoints (employees, groups, stores, menus, modifiers,
  organizations) are no longer anonymous: they require an OIDC access token.
  See [Back-office API (OIDC)](#back-office-api-oidc) below.

## Back-office API (OIDC)

Every route outside `/api/v1/pos/**` and `/health` is a back-office route and
requires a bearer access token from the `pos` realm. The token is validated for
signature, issuer, audience (`pos-api`) and expiry before any controller runs;
the request only proceeds if the token's subject is linked to an active
employee and that account carries the `brand-admin` realm role.

Failures are RFC 9457 problem details, like the rest of the API:

| Situation | Status | Body |
| --------- | ------ | ---- |
| No token, invalid signature, expired, wrong issuer or audience | `401` | `title: "Authentication required"` plus `WWW-Authenticate: Bearer` |
| Valid token, but the subject is unlinked, the employee is inactive, or the role is missing | `403` | `title: "Access denied"` |

The two refusal reasons share one body on purpose: the wire never says whether
a subject exists. The log distinguishes them (`BackOfficeIdentityService`
records the subject and which check failed).

How the boundary is built:

- **The identity↔employee link is `employee.idp_subject`** (Flyway V25),
  nullable and globally unique. Nullable because a POS-only employee needs no
  account; unique because one provider account must not resolve to two
  employees. The brand scope comes from the linked employee's organization, not
  from a token claim — see the migration header for why two sources of truth
  are worse than one.
- **The two credential spaces are disjoint by construction.** POS tokens are
  opaque random strings whose SHA-256 hash is stored; no JWT decoder can accept
  one. Management tokens are JWTs signed by the provider; the POS session lookup
  in the database never matches one. Tests assert both directions.
- **The decoder is built from an explicit JWK set URL** rather than discovered
  from the issuer, so application startup makes no call to the provider and the
  service boots during a provider outage.
- **`brand-admin` is the coarse gate only.** It answers "may this account enter
  the back office at all". Brand confinement now sits underneath it - see
  [Brand confinement](#brand-confinement) - and every list is bounded - see
  [Pagination](#pagination). Franchisee subtrees and job-role permissions
  arrive with M4.

### Brand confinement

A principal's brand is the `organization_id` of the employee linked to its
token subject, re-read on every request. Every management route threads that
value from the controller down to the query predicate:

- **Out-of-brand records answer `404`.** The brand is part of the lookup
  (`findByIdAndOrganizationId`, `existsByIdAndOrganizationId`) rather than a
  check applied after loading a row, so there is no code path that can observe
  another brand's record and then decide to hide it. A foreign ID is
  indistinguishable from a missing one, including for lists, which return only
  the caller's brand.
- **Creating or deleting an organization answers `403`**, with
  `title: "Outside the brand"` and the refused action in `detail`. Brand
  scoping confines a caller to one organization, so those two actions have no
  brand they could be performed in; they belong to a platform-level role.
- **Join rows cannot straddle brands.** `EmployeeGroupMembership` and
  `EmployeeStoreAssignment` resolve their organization from the pair they join
  and reject cross-organization pairs, backed by composite foreign keys
  (V15/V16). The service-level checks are therefore redundant and are gone -
  the constraint lives where it cannot be bypassed.

### Pagination

Every collection endpoint takes `?page` and `?size` and answers with a page
envelope. There is no endpoint that returns a whole table.

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 25,
  "totalElements": 137,
  "totalPages": 6
}
```

- **`page` is zero-based**; a negative value is treated as the first page.
- **`size` defaults to 25 and is capped at 100.** An oversized value is
  *clamped*, not refused: `size=5000` serves 100 rows and reports `size: 100`.
  Clamping keeps an unreasonable request cheap without adding a failure mode to
  a query string; a non-positive size falls back to the default.
- **`size` in the response is the effective size**, not the requested one, so
  `totalPages` can be derived from the body alone. A caller who asked for 5000
  and got `size: 100, totalPages: 6` can see exactly what happened.
- **The envelope is ours, not Spring Data's `Page`.** A serialized `Page`
  carries `pageable`, `sort`, `first`, `last` and `numberOfElements` - a
  library's internal view that would change with the library.
- **Ordering is by primary key ascending.** It is the only ordering that is
  unique, so it is the only one that cannot drop or duplicate a row across a
  page boundary when two rows share a sort value. Where a sub-resource needs
  display order, the derived `OrderByDisplayOrder` clause stays in the query
  and the page's sort is appended after it (`display_order asc, id asc`):
  display order is the presentation, the key is the tie-break.
- **Detail responses are not paged.** `GET /api/v1/menus/{id}` and
  `GET /api/v1/modifier-groups/{id}` embed their children in one aggregate, so
  they carry the full list; the corresponding `.../menu-groups` and
  `.../modifiers` sub-resources are the paged way to read the same rows.

The policy lives in one place, `PageRequests`, so a new list endpoint cannot
accidentally serve the whole table.

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
- `pos.pin.hmac-secret` (dev default only) – keys the deterministic PIN
  fingerprints. It **must** be overridden with a high-entropy value outside
  local development, and it cannot be changed casually: see the fingerprint
  anchor note above.
- `pos.pin.fingerprint-reset` (default `false`) – arm for exactly one startup
  to apply a changed `pos.pin.hmac-secret`, dropping every store claim. Every
  employee must then set their PIN again. Unset it afterwards.
- `pos.identity.issuer` (default `http://localhost:8180/realms/pos`, env
  `OIDC_ISSUER`) – the only issuer whose tokens are accepted. Compared as an
  exact string, so a trailing slash matters.
- `pos.identity.jwk-set-uri` (default the realm's `.../protocol/openid-connect/certs`,
  env `OIDC_JWK_SET_URI`) – where signing keys are fetched.
- `pos.identity.audience` (default `pos-api`, env `OIDC_AUDIENCE`) – the
  audience a token must carry. A genuine token that was never addressed to this
  API is refused, which is what stops a token minted for another client from
  being replayed here.

Limitations: the rate limiter is **process-local** (in-memory) and is not
shared across multiple application instances; a horizontally scaled
deployment would need a shared store (e.g. Redis) for a global limit. The
menu catalog is still global, so a POS session at one store may order any
active menu item — per-store menu scoping, verifying the PIN again on each
transaction, clock-in/out, and role/permission authorization are intentionally
out of scope.

Back-office specifics to know before relying on it:

- The brand is resolved to the principal and enforced on every management
  route, and every list endpoint is paged with a capped size (see
  [Pagination](#pagination)). Do not issue accounts to franchisee-level users
  until M4 lands: brand scope isolates brands from each other but not
  franchisee from franchisee.
- The employee↔identity link has no HTTP surface yet; it is set directly in the
  database (see above). The employee create/update endpoints deliberately do not
  accept `idp_subject`, so an API caller cannot attach an account to an
  employee.
- `brand-admin` is a realm role in Keycloak. An account can hold it and still be
  refused, because the employee link is what makes the role usable.

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
migrations live in `app/src/main/resources/db/migration`
(`V1__create_product_table.sql`, `V2__add_product_sku_and_active.sql`,
`V3__add_product_category.sql`, `V4__add_orders_and_order_lines.sql`) and are
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

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
(e.g. `V1__create_product_table.sql`) and are applied automatically at
application startup. Hibernate runs with `ddl-auto=validate`, so it verifies
the schema against the JPA model instead of modifying it. Databases created
before Flyway was introduced are baselined automatically on first startup
(`spring.flyway.baseline-on-migrate=true`) and are not recreated.

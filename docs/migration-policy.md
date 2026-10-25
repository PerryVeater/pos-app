# Migration policy

Flyway owns the schema. Hibernate only validates it (`ddl-auto=validate`), so a
schema change that is not a migration becomes a startup failure rather than a
silent `ALTER TABLE`.

Migrations live in `app/src/main/resources/db/migration` and are applied at
application startup, in version order, before Hibernate validates the model.

## Rules

1. **Forward-only.** A migration that has been applied anywhere is immutable.
   Flyway stores a checksum per version, so editing an applied file makes every
   existing database fail validation at startup. Corrections get a new version.
2. **One logical change per migration.** Name it
   `V<n>__<snake_case_description>.sql`, numbered sequentially. Never reuse a
   version number and never skip one.
3. **Never rely on `baseline-on-migrate` for a new environment.** It exists so a
   pre-Flyway database can adopt migrations. A new environment starts empty and
   runs V1 onward.
4. **Structure and data are separate migrations.** Adding a `NOT NULL` column to
   a populated table is three steps — add nullable, backfill, tighten — each its
   own version, so a failure leaves a consistent intermediate state.
5. **No migration may depend on application code.** No Spring beans, no service
   classes, no runtime configuration. SQL only, so it behaves identically in CI,
   in a container and on a developer machine.
6. **Every migration must pass on both an empty and a populated database.** If a
   statement would fail against a fresh database, it is wrong.

## Data-destructive statements

`DELETE`, `DROP COLUMN`, `DROP TABLE` and narrowing a type destroy data. They are
allowed only when the affected rows **could not have come from a supported
path** — a removed endpoint, a superseded column, a failed experiment.

The migration header must state, in the file itself:

- what is being removed and why no supported path can produce it,
- which foreign keys force the order of deletion,
- what happens on a freshly migrated database (normally: the statement matches
  no rows).

`V19__make_order_attribution_non_nullable.sql` is the reference example: it
deletes orders without store/employee attribution, which only the removed
unauthenticated management API could have created, and says so before the first
statement.

If the rows *could* have come from a supported path, the statement is not a
migration. It is a data-repair task, and it needs a decision, a backup and
explicit sign-off.

## Verifying a migration

- The integration tests apply the whole chain to a fresh `postgres:16-alpine`
  container, so `mvnw verify` proves it applies cleanly from empty.
- Against a development database, check what was applied:

      SELECT installed_rank, version, description, success
      FROM flyway_schema_history ORDER BY installed_rank;

- `ddl-auto=validate` turns a mismatch between migrations and the JPA model into
  a startup failure. That is the point: the two cannot drift silently.

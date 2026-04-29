# Phase 2 Persistence Foundation

## Goal

Introduce the first database migration and repository testing strategy so the user service has a stable persistence baseline before user/auth business APIs are implemented.

## Requirements

- Add Flyway support for `services/sangui-user-service`.
- Add a first user-service migration under the service-owned schema convention.
- Create a user identity table with required platform columns:
  - `id`
  - `shop_id`
  - `created_at`
  - `updated_at`
  - `deleted`
  - `version`
- Keep user-service table naming under the `ums_` prefix.
- Include unique constraints that protect natural account identities within `shop_id`.
- Add a lightweight migration contract test that checks the SQL file includes required table, columns, indexes, and Flyway naming.
- Update backend database spec with executable Flyway commands, migration naming/location, validation matrix, Good/Base/Bad cases, and repository test strategy.

## Acceptance Criteria

- [ ] `sangui-user-service` declares Flyway/JDBC/MySQL dependencies needed for migration startup.
- [ ] `application.yml` defines datasource and Flyway placeholders using environment variables, without real secrets.
- [ ] Migration file exists at `services/sangui-user-service/src/main/resources/db/migration/V1__create_user_identity_tables.sql`.
- [ ] Migration creates `ums_user` in `sangui_user` with required columns and indexes.
- [ ] Test coverage verifies migration contract without requiring a live database.
- [ ] `.trellis/spec/backend/database-guidelines.md` documents the persistence contract and repository test strategy.

## Technical Notes

- This task changes database infrastructure and schema contracts only. It does not implement register/login APIs or expose user DTOs yet.
- Live Flyway execution against Docker MySQL is desirable for manual verification, but automated CI can begin with SQL contract tests until Testcontainers/repository tests are introduced.

# Add Maven Wrapper

## Goal

Add Maven Wrapper files so SanguiShop can build and test with a project-pinned Maven entry point on machines without a globally installed Maven.

## Requirements

- Add root `mvnw` and `mvnw.cmd` scripts.
- Add `.mvn/wrapper/` metadata required by the wrapper.
- Use a stable Maven version compatible with the current Spring Boot 3.2.5 / Java 21 parent build.
- Keep generated wrapper files scoped to build infrastructure only.

## Acceptance Criteria

- [ ] `.\mvnw.cmd -q test` runs from the repository root.
- [ ] `./mvnw -q test` is available for Unix-like CI runners.
- [ ] No secrets, local machine paths, or generated build outputs are committed.

## Technical Notes

- This is a build/CI infrastructure change and does not modify backend service contracts, API payloads, database schema, Redis keys, MQ topics, or frontend code.

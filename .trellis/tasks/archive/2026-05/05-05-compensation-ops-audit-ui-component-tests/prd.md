# Compensation Ops Audit UI Component Tests

## Goal
Add component-level test coverage for the compensation ops audit search UI so observability open-button behavior is protected beyond model-level URL generation tests.

## Requirements
- Verify Kibana and Loki open buttons are disabled when their generated observability links are empty.
- Verify Kibana and Loki open buttons are enabled when generated observability links are present.
- Verify clicking enabled open buttons calls the dashboard open handler with the expected audit query kind.
- Do not change API contracts, query generation behavior, environment variable names, or production UI behavior.

## Acceptance Criteria
- [ ] A Vue component test covers disabled states for missing Kibana/Loki links.
- [ ] A Vue component test covers enabled states and click behavior for Kibana KQL, Kibana Lucene, and Loki LogQL buttons.
- [ ] Existing model tests still cover URL generation and invalid URL fallback.
- [ ] Frontend lint, typecheck, build, and Vitest pass.

## Technical Notes
- The test may mock `useCompensationDashboard` to keep scope at the component boundary and avoid network calls.
- If component test utilities are not already available, add the minimal dev dependencies needed for Vue 3 component testing.

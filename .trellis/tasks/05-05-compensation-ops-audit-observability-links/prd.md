# Compensation Ops Audit Observability Links

## Goal

Close the compensation ops audit search workflow by letting the dashboard open configured Kibana Discover and Loki Explore destinations from the generated audit query templates, while keeping copy-only behavior available when observability URLs are not configured.

## Requirements

- Add frontend environment variable support for `VITE_KIBANA_DISCOVER_URL` and `VITE_LOKI_EXPLORE_URL`.
- Keep existing generated Kibana KQL, Kibana Lucene, and Loki LogQL templates as the canonical query source.
- Add dashboard actions for `Copy query`, `Open in Kibana`, and `Open in Loki`.
- Build platform links from the active generated query and configured base URL without hardcoding environment-specific hosts.
- Handle missing or invalid observability URLs gracefully without breaking the dashboard.
- Document the new frontend environment contract where operators can find it.

## Acceptance Criteria

- [ ] With no observability URLs configured, generated queries remain copyable and the UI makes open actions unavailable.
- [ ] With `VITE_KIBANA_DISCOVER_URL` configured, the dashboard can open Kibana Discover with the active audit query encoded into the URL.
- [ ] With `VITE_LOKI_EXPLORE_URL` configured, the dashboard can open Loki Explore with the active audit query encoded into the URL.
- [ ] Query generation remains covered by model tests, including configured and unconfigured URL cases.
- [ ] Frontend `typecheck` and `build` pass.

## Technical Notes

- This is frontend-only. No backend API, database, or gateway contract changes are expected.
- Environment variables must remain Vite `VITE_*` public client configuration, not secrets.
- The implementation should reuse existing compensation dashboard model functions and avoid duplicating query construction in Vue template code.
- The docs/spec sync should capture the env keys and expected base URL behavior.

## Plan

1. Research current audit query model and dashboard rendering path.
2. Read frontend component, type-safety, hook/state, API/env, and quality specs relevant to this UI/config change.
3. Add typed env declarations and model helpers for observability platform URLs.
4. Update dashboard UI controls to show copy/open actions based on configuration.
5. Add or extend model tests for URL construction and disabled states.
6. Run `$check`: diff review, spec quality check, lint/typecheck/build as available, then fix issues.
7. Run `$finish-work`: summarize verification and provide user-run git commands.

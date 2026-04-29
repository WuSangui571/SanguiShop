# SanguiShop Frontend Placeholder

The SanguiShop frontend will use Vue 3, TypeScript, and Vite.

Phase 1 intentionally does not generate the full UI project. Future frontend work must follow these boundaries:

- All HTTP traffic goes through Gateway paths under `/api/...`.
- Page code must call domain-specific service modules instead of calling `fetch` directly.
- API DTOs must match backend JSON field names.
- Write requests must include a frontend-generated `requestId`.
- Internal money values use cents and are formatted only at the display layer.
- Token injection belongs in the shared HTTP client/auth store, not in individual components.

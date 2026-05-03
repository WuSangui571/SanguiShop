# SanguiShop Frontend

This workspace now contains a minimal Vue 3 + TypeScript + Vite frontend focused on compensation ops query flows.

## Current Scope

- Compensation dashboard for order/payment history-backed query APIs
- Shared HTTP client with `ApiResult<T>` envelope parsing
- Domain API service module for compensation queries
- Frontend DTOs aligned to backend JSON field names
- Pure model tests for query payload building and summary-card derivation

## Runbook

```bash
cd frontend
npm install
npm run dev
```

Default local dev URL:

- `http://localhost:5173`

## Environment Variables

- `VITE_API_BASE_URL`
  - Optional gateway origin prefix. Leave empty to use the same origin.
- `VITE_DEFAULT_SHOP_ID`
  - Optional default `shopId` used to seed the dashboard filter form.

## Frontend Rules

- All HTTP traffic goes through gateway paths under `/api/...`.
- Page code calls domain-specific service modules instead of `fetch` directly.
- API DTOs match backend JSON field names.
- Write requests must include a frontend-generated `requestId` when the backend contract requires idempotency.
- Internal money values use cents and are formatted only at the display layer.
- Token injection belongs in the shared HTTP client/auth path, not in individual components.

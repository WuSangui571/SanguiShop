# API Type Placeholder

Future frontend DTO files should live here and follow the shared envelope:

```ts
export interface ApiResult<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}
```

Write request DTOs must include `shopId` and `requestId` when the backend contract requires idempotency.

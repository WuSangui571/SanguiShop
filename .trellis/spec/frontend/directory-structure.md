# Frontend Directory Structure

## Recommended Layout

```text
frontend/
|-- src/
|   |-- app/
|   |-- assets/
|   |-- components/
|   |-- composables/
|   |-- layouts/
|   |-- router/
|   |-- services/
|   |-- stores/
|   |-- types/
|   |-- utils/
|   |-- views/
|   |   |-- home/
|   |   |-- product/
|   |   |-- cart/
|   |   |-- order/
|   |   |-- payment/
|   |   |-- seckill/
|   |   |-- ai-chat/
|   |   +-- admin/
|   +-- main.ts
|-- tests/
+-- vite.config.ts
```

## Module Rules

- `views/<domain>` 放页面和该领域私有组件。
- `components` 只放跨页面复用组件，如 PriceText、SkuSelector、ProductCard。
- `services` 按后端领域拆分：`userApi.ts`, `productApi.ts`, `seckillApi.ts`, `orderApi.ts`, `aiApi.ts`。
- `stores` 只存客户端状态和必要缓存，不复制整份后端数据库模型。
- `types/api` 放后端 DTO 类型，字段名与 API JSON 保持一致。

## Forbidden Patterns

- 页面组件直接写 `fetch('/api/...')`，必须走 `services/httpClient`。
- 多个页面复制倒计时、金额格式化、错误码翻译逻辑。
- 组件直接读取 localStorage token，必须走 auth store/http client。
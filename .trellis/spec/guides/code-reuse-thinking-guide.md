# Code Reuse Thinking Guide

## Use This When

- 新增 common 模块、starter、util、composable、组件、API client。
- 看到相似代码出现第 2 次。
- 想把业务逻辑抽到公共层。

## Search First

```bash
rg "Result<|ApiResult|requestId|traceId|shopId" .
rg "formatMoney|useSeckill|httpClient|ErrorCode" frontend/src
rg "BusinessException|GlobalExceptionHandler|Idempot" services common
```

## Reuse Decision

- 技术能力可复用：Result、错误码、traceId、JWT 解析、Redis 序列化、MQ envelope。
- 业务规则不轻易复用：订单状态机、秒杀资格、支付渠道规则应留在所属服务。
- 前端 UI 基础组件可复用；业务组件只有跨页面出现才进入 shared components。

## Avoid Bad Abstractions

- 不为了以后可能多商家做复杂租户框架；但必须保留 `shopId` 契约。
- 不把所有服务 DTO 放进一个巨大 common 包。
- 不把所有 API 请求塞进一个 `api.ts`。
- 不把 AI/RAG prompt 和普通商品搜索逻辑混在一起。
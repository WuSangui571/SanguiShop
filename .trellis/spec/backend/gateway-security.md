# Gateway & Security Guidelines

## Scope / Trigger

涉及 Spring Cloud Gateway、Spring Security、JWT、权限、限流、CORS、密钥、用户输入、管理后台 API 时必须读取本文件。

## Gateway Responsibilities

- 统一入口、路由转发、CORS、JWT 粗鉴权、全局限流、灰度/蓝绿路由。
- 网关不写业务规则，不直接访问业务数据库。
- 内部服务仍需做权限和参数校验，不能把安全完全托付给网关。

## JWT Required Claims

| Claim | 类型 | 说明 |
| --- | --- | --- |
| `sub` | string | 用户 ID |
| `shop_id` | number/string | 当前店铺 ID |
| `roles` | string[] | `USER`, `ADMIN` 等 |
| `permissions` | string[] | 可选，细粒度权限 |
| `iat` / `exp` | number | 签发和过期时间 |
| `jti` | string | Token 唯一 ID，可用于黑名单 |

## Public vs Protected APIs

| API | 鉴权 | 说明 |
| --- | --- | --- |
| `POST /api/users/register` | public | 限流 + 验证码 |
| `POST /api/users/login` | public | 限流 + 防爆破 |
| `GET /api/products/**` | optional | 可匿名浏览，个性化推荐需用户 |
| `POST /api/seckill/**` | required | JWT + 秒杀 token + 限流 |
| `POST /api/orders/**` | required | JWT |
| `POST /api/payments/callback/**` | channel signed | 支付渠道验签，不依赖用户 JWT |
| `/admin/**` | admin required | RBAC + 审计日志 |

## Secret Management

- JWT 私钥、数据库密码、Redis 密码、MQ 密码、支付密钥、模型 API Key 禁止提交到仓库。
- 开发环境使用 `.env.example` 描述变量名，不写真实值。
- 生产环境使用 Vault、Nacos 密文、K8s Secret 或云密钥服务。

## Validation & Error Matrix

| 条件 | code | 处理 |
| --- | --- | --- |
| Token 缺失 | `AUTH_TOKEN_MISSING` | 401 |
| Token 过期 | `AUTH_TOKEN_EXPIRED` | 401，引导刷新登录 |
| 权限不足 | `AUTH_FORBIDDEN` | 403 |
| 限流触发 | `RATE_LIMITED` | 429 |
| 签名错误 | `SIGNATURE_INVALID` | 401/403，记录安全日志 |
| Secret 缺失 | `CONFIG_SECRET_MISSING` | 启动失败，不能降级为空密码 |

## Review Checklist

## Internal Compensation Ops

The compensation dashboard uses gateway-exposed internal APIs:

- `POST /api/users/ops/login`
- `POST /api/users/ops/session/refresh`
- `POST /api/internal/orders/compensation-records/query`
- `POST /api/internal/orders/timeout-replays/manual`
- `POST /api/internal/orders/timeout-replays/bulk`
- `POST /api/internal/payments/compensation-records/query`
- `POST /api/internal/payments/reconciliations/manual`
- `POST /api/internal/payments/reconciliations/bulk`

Rules:

- `POST /api/users/ops/login` is public at gateway level, but user-service must reject authenticated non-ops users with `AUTH_FORBIDDEN`.
- `POST /api/users/ops/session/refresh` is JWT-protected and should only mint a new token for principals that still resolve to configured compensation ops bindings or legacy rollback admins.
- Gateway JWT authentication is required for all of the routes above.
- Browser CORS preflight (`OPTIONS` with `Origin` and `Access-Control-Request-Method`) must pass without JWT rejection.
- Gateway may stay coarse-grained; downstream services own RBAC and must reject principals missing `OPS_COMPENSATION_ADMIN` with `AUTH_FORBIDDEN`.
- Downstream services must also reject trusted principal `shopId` mismatches with `AUTH_FORBIDDEN`.

- [ ] 新 API 已归类 public/protected/admin/internal。
- [ ] 有网关限流规则和服务内兜底保护。
- [ ] 登录失败、管理操作、支付回调、权限拒绝有审计日志。
- [ ] 错误响应不泄漏 SQL、堆栈、模型 prompt、密钥路径。

# Payment Pay MVP

## Goal
实现支付 MVP，让已创建订单可以发起支付并在支付成功后推进为已支付状态，完成从 `created` 到 `paid` 的最小业务闭环。

## Requirements
- 在 `services/sangui-payment-service` 中实现支付订单写入与查询所需的最小运行时能力。
- 新增支付领域表 `pay_payment_order` 与 `pay_callback_log`，通过 Flyway 管理迁移。
- 提供支付发起 API，优先支持 `POST /api/payments`，并评估是否需要同时提供 `POST /api/orders/{orderId}/pay`。
- 支付请求必须使用 `paymentNo` 作为业务幂等键，重复请求不能生成重复支付单。
- 支付成功后推动订单状态从 `created` 更新为 `paid`，且跨服务交互遵循显式契约而非共享数据库。
- 失败路径需要具备清晰的校验与错误映射，包括订单不存在、订单状态不允许支付、重复支付、金额或归属不匹配等情况。
- 增加覆盖迁移契约、应用服务、控制器的自动化测试。
- 同步更新 backend payment contract spec，并在必要处补充 order contract 的支付联动约束。

## Acceptance Criteria
- [ ] `services/sangui-payment-service` 可独立启动并完成 Flyway 迁移。
- [ ] 支付 API 可以为当前登录用户的 `created` 订单创建或复用支付单。
- [ ] 对同一 `paymentNo` 的重复提交返回同一支付结果，不会重复写入支付单。
- [ ] 支付成功后，订单服务中的目标订单状态从 `created` 更新为 `paid`。
- [ ] 订单不属于当前用户、订单不存在、订单状态非 `created`、重复支付等场景都有稳定错误码与测试覆盖。
- [ ] backend spec 已更新为可执行合同文档，包含 API、字段、错误矩阵、测试要求。

## Technical Notes
- 该任务属于 backend + cross-service contract 变更，需要先定义支付 API、订单支付内部契约、数据库字段和错误矩阵，再编码。
- 默认采用服务间 HTTP/internal API 进行订单状态推进，避免 payment-service 直接访问 order-service 数据库。
- 需要复用现有认证模型 `SanguiPrincipal`、Result envelope、Flyway、JdbcTemplate/Repository、WebMvcTest 与应用服务测试模式。
- 如现有网关路由/权限配置已可透传 `/api/payments`，则不额外扩大本次范围；如缺失，仅补最小必要配置。

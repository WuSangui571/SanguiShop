# 补偿运维 Dashboard 认证入口与会话管理闭环

## Goal
把当前“手动向 `sessionStorage` 注入 `sangui.admin.token` 后访问 internal compensation ops API”的临时联调方式，提升为值班人员可直接在系统内完成登录、持有会话、自动处理过期、明确感知无权限状态的完整运维入口。

## Current State
- compensation dashboard 已具备查询、单条 replay、批量 replay、导出与错误文案能力。
- gateway 已允许 dashboard 通过 `/api/internal/orders/**` 和 `/api/internal/payments/**` 访问 internal ops API。
- order/payment compensation ops 当前在服务侧按 `SanguiPrincipal` 执行 `ADMIN` 角色和 `shopId` 一致性校验。
- frontend 目前仍依赖手动写入 `sessionStorage['sangui.admin.token']`，没有登录页、刷新机制、退出机制或统一 401/403 页面态。

## Requirements
- 明确 dashboard 认证入口方案：
  - 优先判断是否复用现有用户登录能力。
  - 若现有登录不足以支撑值班运维闭环，再补最小必要的 internal/admin 登录能力。
- dashboard 需要提供完整的 token 生命周期管理：
  - 登录获取 token
  - 页面刷新后恢复会话
  - token 失效时清理本地会话并回到登录入口
  - 用户主动退出
- 统一认证失败与鉴权失败 UX：
  - `401`：自动清 session，返回登录态，并提示会话已失效或未登录
  - `403`：展示“已认证但无权限”的页面态，而不是静默失败
- 明确 compensation internal ops 的授权模型：
  - 基线方案：继续仅允许 `ADMIN`
  - 若代码结构已具备低成本细分能力，可引入更明确 permission，但必须贯通 JWT claim、gateway 透传和服务校验
- 若变更跨层合同或交互明显，补浏览器级手工验收脚本或 e2e 覆盖登录、过期、无权限、退出等关键路径。

## Acceptance Criteria
- [ ] 值班人员无需手动改浏览器存储，即可进入 dashboard 完成登录并访问 compensation ops 能力。
- [ ] dashboard 能在刷新页面后恢复有效会话，并在 token 无效时自动回到登录入口。
- [ ] `401` 与 `403` 的前端表现可区分，且与后端返回语义一致。
- [ ] internal compensation ops 的角色/权限口径在代码、测试和必要 spec 中一致。
- [ ] 至少有一套可复用的验证方式覆盖登录成功、未登录、无权限、退出、会话恢复这几个核心场景。

## Technical Notes
- 预计涉及前端会话状态、HTTP 拦截/错误归一、dashboard 路由或页面态、user-service 登录返回契约、gateway JWT 白名单与 internal ops 授权口径。
- 该任务属于 frontend + backend + gateway 的跨层改动，实施前需要阅读相关 backend/frontend/guides spec，并在必要时同步更新 code-spec。

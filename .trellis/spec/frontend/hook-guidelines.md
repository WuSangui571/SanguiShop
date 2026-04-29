# Hook / Composable Guidelines

## Composable Rules

- 命名使用 `useXxx`。
- composable 返回状态、动作和错误，不直接渲染 UI。
- 请求类 composable 必须支持 loading/error/retry。
- 需要清理 timer、interval、SSE、WebSocket 时必须在 `onScopeDispose` 中清理。

## Data Fetching

- 所有 HTTP 请求走统一 `httpClient`。
- 业务 API 封装在 `services/*Api.ts`，composable 调用 service。
- 不在组件中拼接 URL。

## Seckill Countdown

- 倒计时基于服务端时间校准。
- 页面切后台再回来时重新校准。
- 到点只允许触发 UI 状态变化，不自动替用户提交下单。

## Polling Rules

- 订单支付状态、秒杀排队状态可以轮询，但必须有最大次数和退避。
- 页面卸载时停止轮询。
- 轮询错误不可无限弹窗。
# Seckill UI Guidelines

## UI State Machine

```text
not_logged_in -> need_login
not_started -> active -> queued -> order_created -> payment_pending -> paid
active -> sold_out
active -> failed_retryable
active -> failed_final
ended
```

## Button Rules

- 未登录：显示登录入口，不请求秒杀接口。
- 未开始：按钮禁用，显示服务端校准倒计时。
- 活动中：允许点击一次，进入 submitting/queued。
- 排队中：禁用重复提交，展示查询状态。
- 库存不足/已购买/已结束：最终失败，不自动重试。

## Time Rules

- 页面加载时获取 `serverTime`。
- 倒计时用 `serverTime + elapsed` 计算。
- 到达开始时间后可刷新 token 或活动状态。

## Tests Required

- 重复点击只发出一次请求。
- 本地时间错误时倒计时仍以 serverTime 为准。
- 401 时跳登录，登录后能回到活动页。
- queued 状态页面卸载后停止轮询。
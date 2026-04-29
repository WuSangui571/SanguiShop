# Component Guidelines

## Component Boundaries

- 页面组件负责路由参数、页面级布局、调用 composable。
- 业务组件负责明确业务 UI，如 SKU 选择、秒杀按钮、订单状态。
- 基础组件不包含业务 API 调用。

## Props and Emits

- Props 使用明确类型，避免 `any`。
- Emits 命名使用动词过去式或命令式：`submitted`, `cancelled`, `retry`。
- 子组件不得直接修改父组件传入对象。

```ts
interface SeckillButtonProps {
  activityId: string
  skuId: string
  serverTime: string
  startsAt: string
  endsAt: string
  disabled?: boolean
}
```

## UI States

每个异步组件至少处理 idle、loading、success、empty、error、retrying / queued。

## E-commerce UI Rules

- 金额展示统一使用 `formatMoney(cents)`。
- 库存、秒杀状态、支付状态必须来自后端或服务端时间判断。
- 商品详情、评论、AI 问答引用来源要可见。
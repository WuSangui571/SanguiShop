# Component Guidelines

## Vue Component Regression Notes

- Do not use a bare `<template>` tag as a visual/layout wrapper inside rendered DOM. Use a real element, a component root fragment, or a directive-bearing `<template v-if>` / `<template v-for>` only when Vue is meant to compile it away. A native HTML `<template>` makes its children inert and can hide controls from component tests and browser interaction.
- If a local pending/submission gate drives a Vue `computed`, `disabled` binding, or loading state, the pending flag must be reactive (`ref` / `reactive`) or be represented by an existing reactive source. A plain closure variable can block duplicate calls internally but will not re-render buttons or recompute UI state.

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

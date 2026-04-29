# SanguiShop Frontend Spec Index

> 前端默认技术栈为 Vue 3 + TypeScript + Vite，面向电商 Web 客户端和可选管理后台。前端必须围绕 Gateway API 契约开发，不绕过网关直连微服务。

## Spec Map

| Spec | 何时读取 | 核心内容 |
| --- | --- | --- |
| [Directory Structure](./directory-structure.md) | 新建页面/模块前 | Vue 项目结构、业务模块划分 |
| [Component Guidelines](./component-guidelines.md) | 写组件前 | 组件边界、Props/Emits、UI 状态 |
| [Hook Guidelines](./hook-guidelines.md) | 写 composable/data fetching 前 | 组合式函数、请求封装、倒计时 |
| [State Management](./state-management.md) | 使用 store/session/cart 前 | Pinia/本地状态/服务端状态 |
| [Type Safety](./type-safety.md) | 定义 API 类型和表单前 | DTO 类型、金额/时间、校验 |
| [API Contracts](./api-contracts.md) | 接入后端接口前 | Result envelope、JWT、错误码 |
| [Seckill UI Guidelines](./seckill-ui-guidelines.md) | 秒杀页面/按钮/倒计时前 | 防重复提交、服务端时间、排队态 |
| [Quality Guidelines](./quality-guidelines.md) | 提交前 | 测试、可访问性、评审清单 |

## Pre-Development Checklist

- [ ] 明确页面属于商城端、管理端还是 AI 客服组件。
- [ ] 先读 API 契约，不根据后端 entity 猜字段。
- [ ] 秒杀和支付页面必须处理 loading、queued、failed、retry、expired 状态。
- [ ] 金额以分为内部单位，展示层统一格式化。
- [ ] Token 只放在约定存储位置，请求层统一注入。
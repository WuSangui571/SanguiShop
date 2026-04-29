# State Management Guidelines

## Store Boundaries

推荐使用 Pinia。

| Store | 内容 | 禁止 |
| --- | --- | --- |
| `authStore` | token、用户摘要、角色 | 保存密码、完整 JWT payload 到日志 |
| `cartStore` | 购物车本地草稿/服务端同步状态 | 作为最终价格来源 |
| `productStore` | 商品列表筛选条件、轻量缓存 | 存整站商品全集 |
| `orderStore` | 当前订单流程状态 | 绕过后端订单状态机 |
| `seckillStore` | 活动状态、token、排队状态 | 客户端自行扣库存 |
| `aiChatStore` | session、消息列表、sources | 存敏感 prompt 或 secret |

## Server State vs Client State

- 价格、库存、支付状态、订单状态属于服务端事实。
- 展开/折叠、当前 tab、表单草稿属于客户端状态。
- 秒杀库存展示可缓存，但提交时以后端结果为准。
# SanguiShop Thinking Guides

> guides 是思考清单，不替代 backend/frontend code-spec。写代码前用它们检查有没有漏掉跨层风险。

## Available Guides

| Guide | Purpose | When to Use |
| --- | --- | --- |
| [Cross-Layer Thinking Guide](./cross-layer-thinking-guide.md) | 跨前端、网关、服务、DB、MQ 的契约思考 | 任何 3 层以上变更 |
| [Code Reuse Thinking Guide](./code-reuse-thinking-guide.md) | 避免重复封装和错误抽象 | 新增 util/common/starter 前 |
| [Architecture Review Checklist](./architecture-review-checklist.md) | 评审微服务设计、部署、可观测、安全 | 开始大功能或 PR review 前 |
| [Seckill Thinking Guide](./seckill-thinking-guide.md) | 秒杀链路专用风险清单 | 秒杀、库存、限流、MQ 改动前 |
| [AI/RAG Thinking Guide](./ai-rag-thinking-guide.md) | AI 问答和推荐风险清单 | 知识库、模型、推荐改动前 |

## Quick Trigger

- [ ] 改动 API/DTO/Event -> 读 backend/microservice-contracts.md。
- [ ] 改动秒杀/库存/订单 -> 读 seckill guide + backend/seckill-contracts.md。
- [ ] 改动 Redis/MQ -> 读 backend/messaging-cache-guidelines.md。
- [ ] 改动 AI -> 读 ai-rag guide + backend/ai-rag-guidelines.md。
- [ ] 改动登录/权限/密钥 -> 读 backend/gateway-security.md。

## Core Habit

先定义契约，再写实现；先找现有模式，再新增抽象；先证明幂等，再谈高并发。
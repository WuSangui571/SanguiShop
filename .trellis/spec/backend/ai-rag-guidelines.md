# AI/RAG Guidelines

## Scope / Trigger

涉及 Spring AI Alibaba、知识库导入、向量数据库、Embedding、ChatClient、RAG Advisor、商品推荐、评论摘要、客服回答时必须读取本文件。

## Service Boundary

AI Service 拥有知识库文档管理、文档切分、Embedding、向量入库、问题重写、检索、Prompt 组装、模型调用、商品问答、推荐解释、评论摘要。AI Service 不直接修改订单、支付、库存等交易状态。

## RAG Request Contract

`POST /api/ai/chat`

```json
{
  "shopId": 1,
  "sessionId": "chat_001",
  "userId": 123,
  "question": "这款手机适合拍夜景吗？",
  "skuId": 10001,
  "requestId": "req_001"
}
```

Response:

```json
{
  "answer": "...",
  "sources": [{"docId":"doc_001","title":"商品参数说明","chunkId":"chunk_09","score":0.82}],
  "confidence": "HIGH",
  "model": "glm-4-flash"
}
```

## Prompt Rules

- 系统提示词必须要求仅基于检索上下文回答，不知道就说不知道。
- 输出商品建议时必须区分事实、推断和推荐理由。
- 不得把系统 prompt、密钥、内部链路、用户隐私输出给用户。
- AI 回答涉及售后、价格、库存时必须引用实时业务服务结果，不只依赖向量库。

## Retrieval Rules

- 默认使用向量检索 + 关键词/精确匹配混合策略。
- Query Transformer 可重写用户问题，但必须保留原问题用于日志和审计。
- 低分检索结果不得强行生成确定答案。
- 每次回答至少记录模型耗时、检索耗时、topK、命中文档、token 估算。

## Tests Required

- 给定 FAQ，断言能命中对应 chunk。
- 无上下文时不得编造答案。
- Prompt 注入要求泄漏系统提示词时必须拒绝。
- 性能测试记录模型 P95、向量库 P95、整体 P95。
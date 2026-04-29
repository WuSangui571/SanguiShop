# AI/RAG Thinking Guide

## Before Coding

- [ ] 用户问题是否必须基于知识库回答？
- [ ] 如果检索不到上下文，是否拒绝编造？
- [ ] 是否需要引用来源？
- [ ] 商品价格、库存、售后政策是否需要实时业务服务校验？
- [ ] 是否记录模型耗时、检索耗时、topK、命中文档？
- [ ] 是否测试 prompt injection？
- [ ] 是否避免输出系统 prompt、secret、用户隐私？
- [ ] 知识库更新后旧向量如何失效？

## Required Specs

- `backend/ai-rag-guidelines.md`
- `backend/gateway-security.md`
- `frontend/api-contracts.md`

## Red Flags

- 直接把用户输入发给 LLM。
- 没有 sources 却给出确定性答案。
- 把模型 API Key 写进前端或仓库。
- 推荐结果绕过商品下架、库存不足、权限限制。
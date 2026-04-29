# Seckill Thinking Guide

## Before Coding

- [ ] 活动状态：未开始、进行中、已结束、暂停是否定义？
- [ ] 是否使用服务端时间，而非前端本地时间？
- [ ] 库存是否预热到 Redis？预热失败怎么办？
- [ ] Redis Lua 返回码是否完整映射为业务错误？
- [ ] 同一用户重复请求如何处理？
- [ ] MQ 发送失败如何补偿？
- [ ] 订单创建失败如何释放库存？
- [ ] 支付超时如何取消订单并释放库存？
- [ ] 压测指标和容量阈值是多少？

## Required Specs

- `backend/seckill-contracts.md`
- `backend/messaging-cache-guidelines.md`
- `backend/microservice-contracts.md`
- `frontend/seckill-ui-guidelines.md`

## Red Flags

- 直接用数据库扣秒杀库存。
- 只靠前端禁用按钮防重复提交。
- Consumer 没有幂等。
- 失败只打日志，没有补偿入口。
- 没有压测就声称支持高并发。
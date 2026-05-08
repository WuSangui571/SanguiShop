# 用户评价图片上传与有图评价闭环

## Goal

补齐用户侧“有图评价”的写入闭环：买家在已完成且未评价订单中选择图片、上传、预览、删除，并在提交评价时携带后端可校验的 `imageUrls`。公开商品评价列表继续使用现有 order-service 投影和 product-service 公共读 API，确保真实上传图片能进入 `withImages=true` 筛选和评分摘要。

## Scope Classification

复杂全栈跨层任务。

触达层：

- backend upload/storage API 边界
- order-service 评价提交校验与幂等
- product-service/order-service 公开评价投影回归测试
- frontend mall 订单评价表单与商品评价刷新
- `.trellis/spec/backend/*` 和 `.trellis/spec/frontend/api-contracts.md`

明确不触碰：

- 订单状态机新增状态
- 支付、退款、库存补偿、物流、售后逆向
- AI 评价总结
- 商家评价管理写操作语义

## Current Context

- Session 58 已完成商品详情评价摘要、评分分布、`withImages` 后端筛选和前端展示。
- 当前 `POST /api/orders/{orderId}/reviews` 支持 `imageUrls` 字段，后端已有数量上限 `6` 与空值校验，幂等比较已包含归一化后的 `imageUrls`。
- 当前前端 `useMallOrderStatus.submitCurrentOrderReview` 固定提交 `imageUrls: []`。
- 仓库检索未发现既有 `UploadController`、`StoragePathResolver` 或可复用 upload API，因此本任务需要先定义最小可复用上传边界。

## Requirements

### Backend Upload Boundary

- 提供买家评价图片上传入口，优先作为可复用 upload/storage 能力落地，而不是把上传逻辑写进订单评价服务。
- 上传 API 必须走 gateway `/api/...`，需要 mall JWT。
- 上传请求只接受图片文件，允许类型建议为 `image/jpeg`, `image/png`, `image/webp`。
- 单文件大小上限建议为 `5MB`；单条评价最多 `6` 张，与现有评价提交上限一致。
- 响应只返回公开可读 URL 或后续可受控读取的 URL。
- 响应不得暴露本地磁盘绝对路径、内部对象 key、trace/operator/audit 字段。
- 上传失败返回统一 `ApiResult` 错误，前端展示 `code/message/traceId`。

### Order Review Submission

- `POST /api/orders/{orderId}/reviews` 继续接收 `imageUrls`。
- 后端校验：
  - 数量不超过 `6`
  - 每项 trim 后非空
  - 长度不超过 `2048`
  - URL 格式合法，且仅允许当前上传边界产生的公开 URL 或明确允许的同源资源 URL
  - 不接受本地路径、相对路径、内部对象 key、`file:` URL、非图片资源引用
- 幂等保持：同一 `(shopId,userId,requestId)` 的 `orderId/rating/content/imageUrls` 归一化结果参与冲突判断。
- 仍然只有 `completed` 订单可评价。
- 公开评价 payload 仍不暴露 raw `userId/orderId/orderNo/requestId/traceId/operator`。

### Frontend Mall Order Review Form

- 在 completed unreviewed 订单评价表单增加图片选择、预览、删除。
- 图片上传 pending 时禁用评价提交。
- 提交评价时携带上传成功后的 `imageUrls`。
- 上传失败必须保留订单详情和评价草稿，并展示后端 `code/message/traceId`。
- 删除预览后不得把对应 URL 带入提交 payload。
- 成功评价后订单详情显示用户已提交图片快照。

### Product Detail Public Review Regression

- 真实上传图片提交后，公开商品评价 item 能展示图片。
- `withImages=true` 能筛到刚提交的有图评价。
- hidden review 不进入 `withImages` 筛选和评分分布。
- hidden reply 不影响图片展示；只隐藏 `merchantReply`。

### Spec And Tests

- 更新 `.trellis/spec/backend/order-create-contracts.md`：
  - 评价 `imageUrls` 校验规则
  - 上传边界引用
  - 幂等 Good/Base/Bad cases
- 更新或新增 backend upload/storage spec：
  - 路由、认证、文件类型/大小、返回字段、错误矩阵、测试要求
- 更新 `.trellis/spec/frontend/api-contracts.md`：
  - 上传 API client 约定
  - 评价表单 pending/error/preview/delete/submit 行为
- 后端测试：
  - 图片 URL 格式校验
  - 数量上限
  - 幂等冲突包含归一化 `imageUrls`
  - 上传 API 文件类型/大小/响应字段边界
  - 公开 projection `withImages` 和 hidden review/reply 回归
- 前端测试：
  - 上传 payload / FormData API client
  - 删除预览
  - 上传失败错误保留
  - 评价提交携带 `imageUrls`
  - 成功提交刷新只看有图结果

## Proposed Technical Plan

1. Research existing HTTP multipart support, gateway route patterns, static resource serving, and local file conventions.
2. Define minimal upload contract and spec before implementation.
3. Implement upload endpoint in the most appropriate existing service after research. Candidate default: product-service or a small common-backed endpoint only if the repo already has static resource support; avoid creating a new review-service surface unless necessary.
4. Add frontend `uploadApi.ts` and typed DTOs using existing `httpClient` auth behavior. Multipart handling may need a narrow `postFormData` helper if `httpClient` only supports JSON.
5. Extend `useMallOrderStatus.submitCurrentOrderReview` to accept `imageUrls` instead of hardcoded `[]`.
6. Extend `MallStorefrontView.vue` review form with file input, upload state, thumbnail previews, remove buttons, and disabled submit while upload is pending.
7. Tighten order-service `imageUrls` normalization/validation for URL/resource boundary.
8. Add backend and frontend targeted tests.
9. Run `$check` quality pass and fix violations.
10. Run `$finish-work` checklist and report exact test and git sync commands.

## Acceptance Criteria

- [ ] Buyer can upload up to 6 valid review images from the completed unreviewed order form.
- [ ] Pending upload disables review submit.
- [ ] Upload failure preserves order detail and rating/content/image draft, and displays `code/message/traceId`.
- [ ] Removing a preview removes that URL from the final review payload.
- [ ] Review submit sends normalized uploaded `imageUrls`.
- [ ] Same `requestId` replay with same normalized `imageUrls` returns the original review.
- [ ] Same `requestId` replay with different normalized `imageUrls` returns `IDEMPOTENCY_CONFLICT`.
- [ ] Completed-order-only review rule remains unchanged.
- [ ] Public product reviews display uploaded image URLs without internal fields.
- [ ] `withImages=true` returns the submitted image review and matching summary.
- [ ] Hidden review stays excluded from list, image filter, and rating distribution.
- [ ] Hidden reply does not hide visible review images.
- [ ] Backend, frontend, and spec docs are updated and targeted tests pass or any sandbox blocker is explicitly reported.

## Open Decisions Before Coding

- Where to host the upload endpoint and static/public file serving in this repo, given no existing upload API was found.
- Whether upload responses should be same-origin URLs such as `/api/uploads/...` or static paths such as `/uploads/...`.
- Whether uploaded files should be validated by MIME type only or also by extension/signature sniffing in the MVP.

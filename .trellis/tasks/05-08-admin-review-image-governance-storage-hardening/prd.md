# 商家评价图片治理与存储可运维加固

## Goal

补齐评价图片成为业务资产后的商家治理与存储运维边界：商家后台能查看、判断、隐藏/恢复带图评价；公开商品详情继续严格排除隐藏评价图片；上传存储合同具备可排查、可备份迁移、可恢复和可手动清理的说明。

## Scope

- 后端 admin review response 暴露公开图片 URL 列表，仍不暴露本地路径、存储根目录、trace/operator 等私有字段。
- 前端 Admin Review Management 列表和详情展示评价图片缩略图；隐藏评价仍可在 admin 侧查看图片。
- 图片加载失败时展示稳定占位和可追踪错误文案，不阻断隐藏、恢复、回复等治理操作。
- 回归公开商品详情边界：hidden review 图片不进入公开列表、`withImages=true`、评分分布；hidden reply 不影响 visible review 图片展示。
- 更新上传存储 runbook/spec：本地目录配置、容量风险、备份/迁移边界、上传失败、文件缺失、读取 404、orphan cleanup 方案。

## Out of Scope

- 不做 AI 评价总结。
- 不把图片内容传入模型。
- 不做自动定时删除。
- 不改变用户原始评价内容或上传资产。
- 不新增数据库迁移，除非现有字段无法满足治理展示。

## Acceptance Criteria

- [ ] Admin review API 列表/详情 payload 包含 `imageUrls` 或等价公开 URL 字段，且不包含本地路径、存储根目录、trace/operator 私有字段。
- [ ] Admin Review Management 可以展示图片缩略图，unknown/空列表 fallback 稳定。
- [ ] 图片加载失败显示占位和可追踪错误，不影响隐藏/恢复评价和回复操作。
- [ ] Hidden review 在 admin 侧仍显示原图/缩略图；公开商品详情刷新后隐藏评价图片消失，恢复后重新出现。
- [ ] Hidden reply 不影响 visible review 图片继续公开展示。
- [ ] 后端测试覆盖 admin response 图片 URL 与字段泄漏边界，以及 hidden review public projection 排除。
- [ ] 前端测试覆盖 admin model 图片列表、加载失败占位/unknown fallback、隐藏/恢复时 admin 图片预览保留。
- [ ] `.trellis/spec/backend/upload-storage-contracts.md` 或相关 spec/runbook 增加可执行运维边界、错误矩阵、Good/Base/Bad case、测试断言点。

## Technical Notes

- 图片 URL 继续以 `/api/uploads/review-images/{fileName}` 为公开治理和公开展示边界。
- Admin 端只消费 public URL，不消费内部文件名以外的存储信息；如果 UI 需要错误追踪，使用前端图片 URL/上下文生成本地错误状态，不要求服务端暴露私有 trace/operator 字段。
- Orphan cleanup 仅做低风险手动/内部方案设计：只清理超过安全窗口且未被任何 `oms_order_review.image_urls` 引用的文件。

# 清理本地运行时产物与收尾手测任务状态

## Current Project Status Summary

根据本轮 `$start`、`.trellis/workspace/codex-agent/journal-1.md` 和 `git status --short`：

- 当前分支：`main`。
- 当前工作树只有 1 个未提交变更：`M deploy/rocketmq/broker-store/config/timercheck`。
- 最近业务主线已可跑通；Ops 权限登录白名单缺口已在 `d8714fe fix:完善Ops后台权限登录白名单` 修复，并由人工手测确认通过。
- `05-12-ops-auth-permission-login-whitelist` 已归档。
- `05-12-manual-existing-feature-test-plan` 仍在 `.trellis/tasks/` 下，状态为 `planning`，但 journal 已记录手测通过并提示应在业务/config 变更处理后归档。
- `deploy/docker-compose.yml` 将 RocketMQ store 绑定为 `./rocketmq/broker-store:/home/rocketmq/store`，因此 `deploy/rocketmq/broker-store/` 是本地运行 RocketMQ broker 时必然变化的运行时目录。
- `git ls-files deploy/rocketmq/broker-store` 显示该运行时目录当前已有文件被 Git 跟踪，导致 `timercheck` 被容器运行改写后污染后续 diff。

## Task Classification

Complex Task.

理由：该任务不改业务逻辑，但同时涉及 Git 跟踪边界、Docker/RocketMQ 本地运行产物、Trellis task 生命周期、ignore/doc 决策和验证命令。需要先明确 contract 和验收矩阵，再由执行端做最小范围修改。

## Goal

清理本地 RocketMQ 运行时产物造成的 Git 污染，并收尾已完成的手测 Trellis task，使后续业务开发只看到有意保留的修改。

## Non-Goals

- 不修改 Java/Vue 业务逻辑。
- 不扩展任何新功能。
- 不调整 Gateway、服务 API、数据库 schema、MQ topic、Redis key 或权限模型。
- 不提交代码。
- 不删除开发者本机真正需要保留的数据，除非只是从 Git index 解除运行时目录跟踪。

## Requirements

- 判断 `deploy/rocketmq/broker-store/`、`deploy/rocketmq/broker-logs/`、`deploy/rocketmq/namesrv-logs/` 是否属于本地运行时产物。
- 若确认属于运行时产物，应添加 ignore 规则，避免后续 Docker Compose 运行继续污染工作树。
- 对已被 Git 跟踪的 RocketMQ runtime 文件，应从 Git index 中移除，但保留本地文件，优先使用 `git rm --cached` 或等效的非破坏性方式。
- 如需要文档化，应在本地运行说明中记录 RocketMQ broker store/logs 为本地运行产物，不应进入业务提交。
- 确认 `05-12-manual-existing-feature-test-plan` 是否满足归档条件；若满足，使用 Trellis task archive 流程归档。
- 归档旧手测任务时不要改写其业务测试内容，只做状态/位置收口。
- 最终 `git status --short` 应只剩有意保留的修改：ignore/docs/Trellis 归档/当前 task 文件，以及从 index 移除 runtime 文件产生的 tracked deletions；不应再出现 `deploy/rocketmq/broker-store/config/timercheck` 的内容修改。

## Command / File Contract

### Commands

```powershell
git status --short
git ls-files deploy/rocketmq/broker-store deploy/rocketmq/broker-logs deploy/rocketmq/namesrv-logs
git rm --cached -r deploy/rocketmq/broker-store
git rm --cached -r deploy/rocketmq/broker-logs
git rm --cached -r deploy/rocketmq/namesrv-logs
python ./.trellis/scripts/task.py archive 05-12-manual-existing-feature-test-plan --no-commit
python ./.trellis/scripts/task.py validate .trellis/tasks/05-12-local-runtime-and-manual-task-hygiene
docker compose -f deploy/docker-compose.yml config
git status --short
```

Notes:

- `git rm --cached` is expected to stage deletions from Git tracking while preserving local files. Do not use destructive filesystem deletion for this task.
- If one of the log directories is not tracked, the command may fail for that path; inspect `git ls-files` first and only untrack tracked runtime paths.
- `task.py archive ... --no-commit` is required because AI should not auto-commit.

### Files / Paths

Expected or likely modified:

- `.gitignore`: add local RocketMQ runtime ignore rules.
- `README.md` or a deploy-local doc if present: optionally document that RocketMQ broker store/logs and namesrv logs are local runtime outputs.
- `.trellis/tasks/archive/2026-05/05-12-manual-existing-feature-test-plan/`: old manual test task after archive.
- `.trellis/tasks/05-12-local-runtime-and-manual-task-hygiene/`: current task files and context.
- `.trellis/.current-task`: points to this cleanup task during execution.

Expected tracked removals from Git index only:

- `deploy/rocketmq/broker-store/**`
- `deploy/rocketmq/broker-logs/**`, only if tracked
- `deploy/rocketmq/namesrv-logs/**`, only if tracked

Must not modify:

- `services/**`
- `frontend/**`
- `common/**`
- `deploy/docker-compose.yml`, unless research proves the bind mount itself is wrong. Current evidence suggests the mount is intentional for local RocketMQ persistence.
- `deploy/rocketmq/broker.conf`, unless only a comment/doc-linked non-behavioral clarification is explicitly needed.

## Validation / Error Matrix

| Check | Expected | If Fails |
| --- | --- | --- |
| `git ls-files deploy/rocketmq/broker-store` | Shows currently tracked runtime files before cleanup. | If empty, do not run `git rm --cached` for that path; only ignore docs may be needed. |
| `.gitignore` after edit | Ignores RocketMQ broker store/logs and namesrv logs while keeping `deploy/rocketmq/broker.conf` tracked. | Narrow ignore patterns; do not ignore all of `deploy/rocketmq/`. |
| `git rm --cached -r ...` | Stages index deletions and preserves local files on disk. | Stop and inspect; do not use `Remove-Item` as a workaround. |
| `task.py archive 05-12-manual-existing-feature-test-plan --no-commit` | Moves old task under `.trellis/tasks/archive/2026-05/`. | If script refuses due status/metadata, record blocker and leave task unarchived. |
| `docker compose -f deploy/docker-compose.yml config` | Compose config renders successfully. | Fix only docs/ignore mistakes; do not change service behavior without approval. |
| Final `git status --short` | No `M deploy/rocketmq/broker-store/config/timercheck`; only intentional ignore/docs/Trellis/index-removal changes remain. | Re-check whether runtime path is still tracked or ignore rules are too narrow. |

## Good / Base / Bad Cases

Good:

- Runtime store/log files are untracked from Git index and ignored going forward.
- `deploy/rocketmq/broker.conf` remains tracked.
- Existing local RocketMQ files remain on disk for developer convenience.
- Old manual test task is archived because journal and human feedback indicate it passed.
- Final status is understandable and contains no accidental business implementation changes.

Base:

- Only `broker-store` is currently tracked; logs are ignored only for future safety.
- Old manual test task cannot be archived automatically; PRD/check output records the exact blocker and leaves it active.
- Documentation is limited to `.gitignore` comments if README has no suitable local run section.

Bad:

- Runtime files are deleted from disk with a destructive command when index untracking would suffice.
- `deploy/rocketmq/` is ignored wholesale and accidentally hides `broker.conf`.
- Business service/frontend files are modified.
- `task.py archive` auto-commits or a commit is created by the AI.
- `timercheck` remains as a modified tracked file after cleanup.

## Required Tests And Assertion Points

Minimum required:

```powershell
git status --short
git ls-files deploy/rocketmq/broker-store deploy/rocketmq/broker-logs deploy/rocketmq/namesrv-logs
docker compose -f deploy/docker-compose.yml config
python ./.trellis/scripts/task.py validate .trellis/tasks/05-12-local-runtime-and-manual-task-hygiene
git status --short
```

Optional if README/docs changed only:

```powershell
rg -n "broker-store|broker-logs|namesrv-logs|RocketMQ" README.md deploy .gitignore
```

Assertion points:

- Compose config still includes `rocketmq-namesrv` and `rocketmq-broker`.
- Compose config still mounts `./rocketmq/broker.conf` read-only.
- Ignore rules cover runtime directories without hiding broker config.
- Old manual test task is either archived under `.trellis/tasks/archive/2026-05/` or a blocker is documented.
- `git status --short` contains no business implementation file changes.

## Relevant Specs

- `.trellis/spec/backend/observability-devops.md`: local Docker Compose dependency contract; RocketMQ is part of local dependency setup; secrets/runtime outputs should not enter repo state.
- `.trellis/spec/backend/messaging-cache-guidelines.md`: RocketMQ belongs to MQ infrastructure; task must not change topic/event contracts.
- `.trellis/spec/backend/quality-guidelines.md`: completion requires scoped changes, no secrets/debug runtime artifacts, and relevant verification.
- `.trellis/spec/guides/architecture-review-checklist.md`: confirms this is operational hygiene, not a service boundary/API change.
- `.trellis/workflow.md`: task archive/start/context lifecycle and no AI commit rule.

## Code Patterns Found

- `.gitignore` already ignores build outputs, local `.env`, IDE files, `node_modules/`, and `dist/`, but has no RocketMQ runtime ignores.
- `deploy/docker-compose.yml` uses named volumes for MySQL/Redis/Nacos, but RocketMQ namesrv/broker logs and broker store are bind-mounted under `deploy/rocketmq/`.
- `deploy/rocketmq/broker.conf` configures:
  - `storePathRootDir=/home/rocketmq/store`
  - `storePathCommitLog=/home/rocketmq/store/commitlog`
  - `storePathConsumeQueue=/home/rocketmq/store/consumequeue`
  - `storePathIndex=/home/rocketmq/store/index`
  - `storeCheckpoint=/home/rocketmq/store/checkpoint`
  - `abortFile=/home/rocketmq/store/abort`
- `git ls-files deploy/rocketmq/broker-store` currently lists RocketMQ runtime files, meaning they are tracked and must be removed from the index for ignore rules to take effect.
- `.trellis/tasks/05-12-manual-existing-feature-test-plan/task.json` still says `status: planning`; journal says human manual testing passed and previous record step deferred archival only because runtime/business/config changes remained.

## Files Likely To Modify

- `.gitignore`: add narrow runtime ignore entries.
- `README.md`: optionally add one local development note if no better deploy docs exist.
- Git index for `deploy/rocketmq/broker-store/**` and possibly RocketMQ log directories if tracked.
- `.trellis/tasks/archive/2026-05/05-12-manual-existing-feature-test-plan/**`: archive output.
- `.trellis/tasks/05-12-local-runtime-and-manual-task-hygiene/**`: current task PRD/context.

## Risk / Boundary Notes

- This task is allowed to change repository hygiene and Trellis metadata only.
- Because `broker-store` is already tracked, adding `.gitignore` alone will not fix current diff; tracked files must be removed from the index.
- `git rm --cached -r` is non-destructive to local files but stages deletions. The final diff should be reviewed as intentional removal of runtime files from repository tracking.
- Do not run `git clean`, `git reset`, `git checkout --`, or recursive filesystem deletes.
- Do not archive the current cleanup task during implementation; it should stay active until Codex check/finish-work later.
- If the old manual test task has unrecorded useful acceptance notes, preserve them by archive move only; do not rewrite the PRD.

## Acceptance Criteria

- [ ] Trellis task directory exists with this PRD.
- [ ] Required spec/context is initialized for implement and check.
- [ ] Runtime path decision is documented: RocketMQ store/log bind mounts are local runtime outputs.
- [ ] `.gitignore` or equivalent ignore mechanism prevents future RocketMQ store/log diffs.
- [ ] Tracked RocketMQ runtime files are removed from Git index without deleting local files.
- [ ] `05-12-manual-existing-feature-test-plan` is archived if script allows and archival criteria are met.
- [ ] `docker compose -f deploy/docker-compose.yml config` passes.
- [ ] Final `git status --short` has no modified `deploy/rocketmq/broker-store/config/timercheck` entry and no business implementation changes.

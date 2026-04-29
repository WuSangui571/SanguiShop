# Journal - sangui (Part 1)

> AI development session journal
> Started: 2026-04-29

---

## Session 1: SanguiShop Phase 1 Foundation Scaffold

**Date**: 2026-04-29
**Task**: `phase-1-foundation`
**Branch**: `main`

### Summary

Built the first-phase SanguiShop foundation scaffold with Maven multi-module structure, common technical modules, service shells, local dependency placeholders, and documentation.

### Main Changes

- Added the root Maven parent project and module aggregation for `common/` and `services/`.
- Added common modules for API envelopes, error codes, tracing, JWT claim constants, Redis key naming, MQ event envelopes, and observability field names.
- Added Gateway and business service shells for user, product, seckill, order, payment, logistics, review, marketing, search recommendation, and AI.
- Added local `deploy/docker-compose.yml` and `.env.example` for MySQL, Redis, Nacos, and RocketMQ.
- Added frontend placeholder docs and phase-1 foundation documentation.
- Updated backend DevOps spec with the phase-1 local environment contract.

### Git Commits

| Hash | Message |
|------|---------|
| `2d4df65` | `feat: 新增项目的基础脚手架` |

### Testing

- [OK] `mvn -q -DskipTests compile` passed.
- [WARN] `mvn -q test` and `mvn -q -DskipTests package` were blocked in the Codex sandbox by access to `C:\Users\CodexSandboxOffline\.m2\repository`.

### Status

[OK] **Completed**

### Next Steps

- Run local verification outside the sandbox if Maven can access the normal local repository.
- Start the next task only after deciding which business domain should be implemented first.



## Session 2: 补 Maven Wrapper

**Date**: 2026-04-29
**Task**: 补 Maven Wrapper
**Branch**: `main`

### Summary

为项目补充 Maven Wrapper 入口 mvnw、mvnw.cmd 和 .mvn/wrapper 配置，固定 Maven 3.9.9；同步后端 DevOps/Quality 规范改为优先使用 wrapper 命令，并补充跨平台换行约束。已验证 mvnw.cmd -v 可运行；测试/编译在当前沙箱因 Maven 依赖网络解析被拦截未完成。

### Main Changes

- Added Maven Wrapper entrypoints `mvnw` and `mvnw.cmd`.
- Added `.mvn/wrapper/maven-wrapper.properties` pinned to Apache Maven 3.9.9 with SHA-512 verification.
- Added `.gitattributes` rules for wrapper line endings across Windows and Unix CI.
- Updated backend DevOps and Quality specs to prefer wrapper-based Maven commands.

### Git Commits

| Hash | Message |
|------|---------|
| `ce2b520` | feat:补 Maven Wrapper |

### Testing

- [OK] `.\mvnw.cmd -v` reports Apache Maven 3.9.9.
- [WARN] `.\mvnw.cmd -q test` and `.\mvnw.cmd -q -DskipTests compile` were attempted but blocked by sandboxed Maven dependency resolution/network access.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: Phase 1 Quality Closure

**Date**: 2026-04-29
**Task**: Phase 1 Quality Closure
**Branch**: `main`

### Summary

为第一阶段脚手架建立最小质量闭环：补充 common 契约单元测试、Gateway 和 User 服务 smoke test、GitHub Actions CI、本地 scripts/verify.ps1、Docker Compose config 校验入口、Maven Enforcer Java/Maven 版本约束，并同步 phase-1 文档与后端质量/DevOps spec。

### Main Changes

- Added common contract tests for `ApiResult`, `CommonErrorCode`, `RedisKeyBuilder`, and `EventEnvelope`.
- Added Gateway and User service smoke tests to verify startup classes and basic Spring context loading.
- Added `.github/workflows/ci.yml` and `scripts/verify.ps1` for Maven tests, package verification, and Docker Compose config validation.
- Added Maven Enforcer rules for Java 21 and Maven 3.9.9+.
- Updated `docs/phase-1-foundation.md` plus backend DevOps/Quality specs with executable verify commands and required assertion points.

### Git Commits

| Hash | Message |
|------|---------|
| `c955df4` | feat:最小单元测试、CI/verify 脚本、Docker Compose 配置校验和基础构建规范 |

### Testing

- [OK] Human reported testing completed before recording.
- [OK] Commit `c955df4` exists on `main`.
- [OK] Working directory was clean before session recording.

### Status

[OK] **Completed**

### Next Steps

- None - task complete

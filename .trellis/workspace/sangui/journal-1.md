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

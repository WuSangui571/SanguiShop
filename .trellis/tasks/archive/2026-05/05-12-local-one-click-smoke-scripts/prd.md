# Local One-Click Smoke Scripts

## Goal

沉淀一组本地一键启动/验收 smoke 入口，把当前人工可跑通的环境、Git hygiene、后端编译/测试和前端构建验证固化为便宜、可重复的 Windows 友好流程。

本轮只沉淀验证流程，不改业务逻辑，不新增 API，不修改数据库、Redis、MQ、权限或前端功能行为。

## Scope Classification

- Type: Complex Task
- Area: local DevOps / scripts / docs / smoke validation
- Touches: Docker Compose config, Git ignore/tracked boundary, Maven Wrapper command entry, optional frontend npm command entry, documentation/spec sync
- Does not touch: business Java/Vue implementation, controller/API contracts, DB schema, Redis keys, MQ topics, authentication/authorization logic

## Command Contract

Implementation should prefer a repo-local PowerShell entrypoint under `scripts/`, for example:

```powershell
.\scripts\smoke-local.ps1 [-SkipDocker] [-SkipBackend] [-SkipFrontend] [-BackendMode <compile|test>] [-PrintCommandOnly]
```

Required behavior:

- Default run validates Git hygiene, Docker Compose config, backend lightweight compile, and frontend typecheck/build if frontend scripts exist.
- `-SkipDocker` skips Docker/Compose checks for machines without Docker.
- `-SkipBackend` skips Maven checks.
- `-SkipFrontend` skips npm checks.
- `-BackendMode compile` runs lightweight Maven compile with `-DskipTests`.
- `-BackendMode test` may run a targeted smoke test set if a cheap existing target is selected and documented.
- `-PrintCommandOnly` prints resolved commands and exits successfully without invoking them.

Required command rules:

- Backend must use project Maven Wrapper:
  - Windows: `.\mvnw.cmd`
  - Non-Windows/pwsh-compatible fallback if implemented: `./mvnw`
- When `MAVEN_USER_HOME` is unset, script should set it to `<repo>\.m2`.
- Maven command should include repo-local Maven cache:
  - `"-Dmaven.repo.local=<repo>\.m2\repository"`
- Frontend commands must be Windows policy friendly:
  - `cmd /c npm --prefix frontend run typecheck`
  - `cmd /c npm --prefix frontend run build`
- Docker Compose config check:
  - `docker compose -f deploy/docker-compose.yml config`
- Git hygiene checks must verify:
  - `deploy/rocketmq/broker-store/` is ignored
  - `deploy/rocketmq/broker-logs/` is ignored
  - `deploy/rocketmq/namesrv-logs/` is ignored
  - `deploy/rocketmq/broker.conf` remains tracked

## Validation And Error Matrix

| Area | Validation | Expected | Failure Meaning |
| --- | --- | --- | --- |
| Compose config | `docker compose -f deploy/docker-compose.yml config` | Renders successfully | Docker Compose config invalid or environment interpolation broke local dependency setup |
| MySQL/Redis/Nacos/RocketMQ status | Optional container status check if implemented | Clear pass/skip/fail output | Local dependency container is not running or Docker unavailable |
| RocketMQ ignore | `git check-ignore` for store/log runtime sample paths | Runtime paths are ignored | Runtime artifacts may pollute future diffs |
| RocketMQ config tracked | `git ls-files deploy/rocketmq/broker.conf` | `broker.conf` is listed | Source config accidentally untracked/ignored |
| Runtime dirs untracked | `git ls-files deploy/rocketmq/broker-store deploy/rocketmq/broker-logs deploy/rocketmq/namesrv-logs` | Empty output | Runtime files are tracked and need index cleanup |
| Backend compile | Maven Wrapper `-DskipTests compile` | Compile succeeds | Java/backend contracts or dependencies broken |
| Backend targeted test | Optional documented smoke target | Expected test classes run | Smoke selector wrong or backend smoke regression |
| Frontend typecheck | `cmd /c npm --prefix frontend run typecheck` | TypeScript passes | Frontend type contract broken |
| Frontend build | `cmd /c npm --prefix frontend run build` | Vite build passes | Frontend bundle/build regression |
| Print-only | `-PrintCommandOnly` | Prints commands, no side effects | Script is hard to review and unsafe for handoff |

Exit code policy:

- `0`: all selected checks passed or were explicitly skipped.
- Non-zero: selected check failed; output must identify the failing command/area.
- Skipped checks must be explicit in output and must not be reported as passed.

## Good / Base / Bad Cases

Good:

- `.\scripts\smoke-local.ps1` runs Git hygiene, Compose config, backend compile, frontend typecheck, and frontend build successfully.
- `.\scripts\smoke-local.ps1 -PrintCommandOnly` prints the resolved Git/Docker/Maven/npm commands without running them.
- RocketMQ runtime files remain ignored while `deploy/rocketmq/broker.conf` remains tracked.
- Backend command uses `.\mvnw.cmd` and repo-local `.m2\repository`, not global `mvn`.
- Frontend command uses `cmd /c npm --prefix frontend ...`, avoiding PowerShell npm shim policy issues.

Base:

- `.\scripts\smoke-local.ps1 -SkipDocker` is acceptable when Docker is unavailable locally, but output must clearly mark Docker checks skipped.
- If no cheap targeted backend smoke test is selected, `-BackendMode compile` is acceptable as the default lightweight backend gate.
- If frontend package scripts are unavailable or frontend is intentionally not installed, `-SkipFrontend` is acceptable for backend-only local validation.
- Optional container status checks may be limited to Compose service existence/status and should not require destructive restart.

Bad:

- Script deletes `deploy/rocketmq/broker-store/`, `broker-logs/`, or `namesrv-logs` from disk as part of smoke validation.
- Script ignores all of `deploy/rocketmq/`, causing `broker.conf` to be hidden.
- Docs tell users to run global `mvn`.
- Frontend docs require direct `npm` PowerShell shim execution on Windows.
- Script starts or stops business services by default without an explicit flag.
- Business Java/Vue implementation files are changed to satisfy this task.

## Documentation Requirements

Update the local run/smoke section in one of:

- `.trellis/spec/backend/observability-devops.md`
- `README.md` if the repository already has a local running section

Docs must include:

- Default one-click smoke command.
- Skip flags and when to use them.
- What each check proves.
- Required interpretation of skipped vs passed checks.
- RocketMQ tracked/ignored boundary.
- Windows frontend command shape using `cmd /c npm --prefix frontend ...`.

## Required Tests And Assertion Points

Implementation verification must include:

- `.\scripts\smoke-local.ps1 -PrintCommandOnly`
  - Assert Maven Wrapper command is printed.
  - Assert Docker Compose config command is printed.
  - Assert Git hygiene commands are printed.
  - Assert frontend npm commands are printed or documented as skipped by flag.
- Git hygiene direct checks:
  - `git check-ignore -v deploy/rocketmq/broker-store/config/timercheck deploy/rocketmq/broker-logs/rocketmqlogs/broker.log deploy/rocketmq/namesrv-logs/rocketmqlogs/namesrv.log`
  - `git ls-files deploy/rocketmq/broker.conf deploy/rocketmq/broker-store deploy/rocketmq/broker-logs deploy/rocketmq/namesrv-logs`
- Compose config:
  - `docker compose -f deploy/docker-compose.yml config`
- Backend lightweight gate:
  - `.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" -DskipTests compile`
- Frontend gates if frontend participates:
  - `cmd /c npm --prefix frontend run typecheck`
  - `cmd /c npm --prefix frontend run build`
- Task validation:
  - `python .\.trellis\scripts\task.py validate .trellis\tasks\05-12-local-one-click-smoke-scripts`

## Non-Goals

- No business API changes.
- No DB migration changes.
- No Redis key/MQ topic changes.
- No auth/permission behavior changes.
- No Docker image build pipeline redesign.
- No Git index cleanup unless implementation discovers tracked runtime files and the human explicitly approves cleanup.

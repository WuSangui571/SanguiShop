param(
    [switch]$SkipDocker,
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [ValidateSet("compile", "test")]
    [string]$BackendMode = "compile",
    [switch]$PrintCommandOnly
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$exitCode = 0

function Write-CheckCommand {
    param([string]$Label, [string]$Command)
    Write-Host "[$Label] $Command"
}

# ===================== Git Hygiene =====================
Write-Host "== Git hygiene =="

$gitIgnoreCmd = "git check-ignore -v deploy/rocketmq/broker-store/config/timercheck deploy/rocketmq/broker-logs/rocketmqlogs/broker.log deploy/rocketmq/namesrv-logs/rocketmqlogs/namesrv.log"
$gitLsFilesCmd1 = "git ls-files deploy/rocketmq/broker.conf"
$gitLsFilesCmd2 = "git ls-files deploy/rocketmq/broker-store deploy/rocketmq/broker-logs deploy/rocketmq/namesrv-logs"

if ($PrintCommandOnly) {
    Write-CheckCommand "git" $gitIgnoreCmd
    Write-CheckCommand "git" $gitLsFilesCmd1
    Write-CheckCommand "git" $gitLsFilesCmd2
} else {
    & git check-ignore -q deploy/rocketmq/broker-store/config/timercheck 2>$null
    if ($LASTEXITCODE -eq 0) { Write-Host "  PASS: broker-store runtime paths are ignored" }
    else { Write-Host "  FAIL: broker-store is NOT ignored"; $exitCode = 1 }

    & git check-ignore -q deploy/rocketmq/broker-logs/rocketmqlogs/broker.log 2>$null
    if ($LASTEXITCODE -eq 0) { Write-Host "  PASS: broker-logs runtime paths are ignored" }
    else { Write-Host "  FAIL: broker-logs is NOT ignored"; $exitCode = 1 }

    & git check-ignore -q deploy/rocketmq/namesrv-logs/rocketmqlogs/namesrv.log 2>$null
    if ($LASTEXITCODE -eq 0) { Write-Host "  PASS: namesrv-logs runtime paths are ignored" }
    else { Write-Host "  FAIL: namesrv-logs is NOT ignored"; $exitCode = 1 }

    $brokerConf = & git ls-files deploy/rocketmq/broker.conf
    if ($LASTEXITCODE -eq 0 -and $brokerConf) { Write-Host "  PASS: broker.conf is tracked" }
    else { Write-Host "  FAIL: broker.conf is NOT tracked"; $exitCode = 1 }

    $runtimeTracked = & git ls-files deploy/rocketmq/broker-store deploy/rocketmq/broker-logs deploy/rocketmq/namesrv-logs 2>$null
    if ($runtimeTracked) {
        Write-Host "  FAIL: runtime directories are tracked in git index"
        $runtimeTracked | ForEach-Object { Write-Host "    $_" }
        Write-Host "  Resolve only after human approval with git rm --cached for the listed runtime paths."
        $exitCode = 1
    }
    else { Write-Host "  PASS: runtime directories are not tracked" }
}

# ===================== Docker Compose =====================
if ($SkipDocker) {
    Write-Host "== Docker Compose config (skipped by -SkipDocker) =="
} else {
    Write-Host "== Docker Compose config =="
    $dockerCmd = "docker compose -f deploy/docker-compose.yml config --quiet"

    if ($PrintCommandOnly) {
        Write-CheckCommand "docker" $dockerCmd
    } else {
        $dockerExe = Get-Command docker -ErrorAction SilentlyContinue
        if (-not $dockerExe) {
            Write-Host "  FAIL: Docker CLI not found (use -SkipDocker to skip this selected check)"
            $exitCode = 1
        } else {
            & docker compose -f deploy/docker-compose.yml config --quiet
            if ($LASTEXITCODE -ne 0) { Write-Host "  FAIL: Docker Compose config is invalid"; $exitCode = 1 }
            else { Write-Host "  PASS: Docker Compose config renders successfully" }
        }
    }
}

# ===================== Backend =====================
if ($SkipBackend) {
    Write-Host "== Backend (skipped by -SkipBackend) =="
} else {
    if (-not $env:MAVEN_USER_HOME) {
        $env:MAVEN_USER_HOME = Join-Path $repoRoot ".m2"
    }
    $mavenRepoLocal = Join-Path $env:MAVEN_USER_HOME "repository"

    if ($BackendMode -eq "compile") {
        $mavenArgs = @("-q", "-Dmaven.repo.local=$mavenRepoLocal", "-DskipTests", "compile")
        $modeLabel = "compile"
    } else {
        $mavenArgs = @("-q", "-Dmaven.repo.local=$mavenRepoLocal", "test")
        $modeLabel = "test"
    }

    Write-Host "== Backend $modeLabel =="
    Write-Host "  Maven wrapper: .\mvnw.cmd"
    Write-Host "  Maven repo local: $mavenRepoLocal"

    if ($PrintCommandOnly) {
        Write-CheckCommand "mvnw" ".\mvnw.cmd $($mavenArgs -join ' ')"
    } else {
        & ".\mvnw.cmd" @mavenArgs
        if ($LASTEXITCODE -ne 0) { Write-Host "  FAIL: Backend $modeLabel failed"; $exitCode = 1 }
        else { Write-Host "  PASS: Backend $modeLabel succeeded" }
    }
}

# ===================== Frontend =====================
if ($SkipFrontend) {
    Write-Host "== Frontend (skipped by -SkipFrontend) =="
} else {
    $frontendPkg = Join-Path (Join-Path $repoRoot "frontend") "package.json"
    if (-not (Test-Path $frontendPkg)) {
        Write-Host "== Frontend (frontend/package.json not found, skipped) =="
    } else {
        $typecheckCmd = "cmd /c npm --prefix frontend run typecheck"
        $buildCmd = "cmd /c npm --prefix frontend run build"

        Write-Host "== Frontend typecheck =="
        if ($PrintCommandOnly) {
            Write-CheckCommand "npm" $typecheckCmd
        } else {
            & cmd /c "npm --prefix frontend run typecheck"
            if ($LASTEXITCODE -ne 0) { Write-Host "  FAIL: Frontend typecheck failed"; $exitCode = 1 }
            else { Write-Host "  PASS: Frontend typecheck succeeded" }
        }

        Write-Host "== Frontend build =="
        if ($PrintCommandOnly) {
            Write-CheckCommand "npm" $buildCmd
        } else {
            & cmd /c "npm --prefix frontend run build"
            if ($LASTEXITCODE -ne 0) { Write-Host "  FAIL: Frontend build failed"; $exitCode = 1 }
            else { Write-Host "  PASS: Frontend build succeeded" }
        }
    }
}

# ===================== Summary =====================
if ($PrintCommandOnly) {
    Write-Host ""
    Write-Host "PrintCommandOnly set; no commands were executed."
    exit 0
}

Write-Host ""
if ($exitCode -eq 0) { Write-Host "All selected checks passed." }
else { Write-Host "Some checks failed." }
exit $exitCode

param(
    [switch]$SkipDocker
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $env:MAVEN_USER_HOME) {
    $env:MAVEN_USER_HOME = Join-Path $repoRoot ".m2"
}
$mavenRepoLocal = Join-Path $env:MAVEN_USER_HOME "repository"

Write-Host "== Maven tests =="
& ".\mvnw.cmd" -q "-Dmaven.repo.local=$mavenRepoLocal" test
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "== Maven package =="
& ".\mvnw.cmd" -q "-Dmaven.repo.local=$mavenRepoLocal" "-DskipTests" package
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if (-not $SkipDocker) {
    Write-Host "== Docker Compose config =="
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        throw "Docker CLI is required for Compose validation. Re-run with -SkipDocker to skip this local-only check."
    }

    & docker compose -f deploy/docker-compose.yml config
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

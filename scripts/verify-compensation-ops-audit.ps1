param(
    [ValidateSet("all", "order", "payment")]
    [string]$Service = "all",

    [string]$MavenRepoLocal
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $MavenRepoLocal) {
    if (-not $env:MAVEN_USER_HOME) {
        $env:MAVEN_USER_HOME = Join-Path $repoRoot ".m2"
    }

    $MavenRepoLocal = Join-Path $env:MAVEN_USER_HOME "repository"
} elseif (-not [System.IO.Path]::IsPathRooted($MavenRepoLocal)) {
    $MavenRepoLocal = Join-Path $repoRoot $MavenRepoLocal
}

$targets = @{
    order = @{
        Module = "services/sangui-order-service"
        Test = "InternalOrderCompensationControllerTest"
    }
    payment = @{
        Module = "services/sangui-payment-service"
        Test = "InternalPaymentCompensationControllerTest"
    }
}

if ($Service -eq "all") {
    $selectedServices = @("order", "payment")
} else {
    $selectedServices = @($Service)
}

$modules = $selectedServices | ForEach-Object { $targets[$_].Module }
$tests = $selectedServices | ForEach-Object { $targets[$_].Test }
$moduleSelector = $modules -join ","
$testSelector = $tests -join ","

Write-Host "== Compensation ops audit controller tests =="
Write-Host "Service selection: $Service"
Write-Host "Maven repo local: $MavenRepoLocal"
Write-Host "Maven modules: $moduleSelector"
Write-Host "Expected Maven output should show:"
foreach ($test in $tests) {
    Write-Host "  - $test"
}

& ".\mvnw.cmd" `
    -q `
    "-Dmaven.repo.local=$MavenRepoLocal" `
    -pl $moduleSelector `
    -am `
    "-Dtest=$testSelector" `
    "-Dsurefire.failIfNoSpecifiedTests=false" `
    test

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Compensation ops audit controller test command completed."
Write-Host "Confirm the Maven output above includes the expected test class name(s)."

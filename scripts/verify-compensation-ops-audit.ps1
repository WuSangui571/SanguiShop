param(
    [ValidateSet("all", "order", "payment")]
    [string]$Service = "all",

    [string]$MavenRepoLocal,

    [switch]$PrintCommandOnly
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Format-CommandArgument {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Argument
    )

    if ($Argument -notmatch '[\s"]') {
        return $Argument
    }

    return '"' + ($Argument -replace '"', '\"') + '"'
}

if (-not $MavenRepoLocal) {
    if (-not $env:MAVEN_USER_HOME) {
        $env:MAVEN_USER_HOME = Join-Path $repoRoot ".m2"
    }

    $MavenRepoLocal = Join-Path $env:MAVEN_USER_HOME "repository"
} elseif (-not [System.IO.Path]::IsPathRooted($MavenRepoLocal)) {
    $MavenRepoLocal = Join-Path $repoRoot $MavenRepoLocal
}
$MavenRepoLocal = [System.IO.Path]::GetFullPath($MavenRepoLocal)

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
$isWindowsHost = if ($null -ne (Get-Variable -Name IsWindows -ErrorAction SilentlyContinue)) {
    $IsWindows
} else {
    $true
}
$mavenExecutable = if ($isWindowsHost) {
    ".\mvnw.cmd"
} else {
    "./mvnw"
}
$mavenArgs = @(
    "-q",
    "-Dmaven.repo.local=$MavenRepoLocal",
    "-pl",
    $moduleSelector,
    "-am",
    "-Dtest=$testSelector",
    "-Dsurefire.failIfNoSpecifiedTests=false",
    "test"
)
$expandedCommand = (@($mavenExecutable) + $mavenArgs | ForEach-Object { Format-CommandArgument $_ }) -join " "

Write-Host "== Compensation ops audit controller tests =="
Write-Host "Service selection: $Service"
Write-Host "Maven executable: $mavenExecutable"
Write-Host "Maven repo local: $MavenRepoLocal"
Write-Host "Module selector: $moduleSelector"
Write-Host "Test selector: $testSelector"
Write-Host "Expanded Maven command: $expandedCommand"
Write-Host "Expected Maven output should show:"
foreach ($test in $tests) {
    Write-Host "  - $test"
}

if ($PrintCommandOnly) {
    Write-Host "PrintCommandOnly set; Maven command was not executed."
    exit 0
}

& $mavenExecutable @mavenArgs

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Compensation ops audit controller test command completed."
Write-Host "Confirm the Maven output above includes the expected test class name(s)."

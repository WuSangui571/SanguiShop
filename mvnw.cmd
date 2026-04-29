@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "MAVEN_VERSION=3.9.9"
set "MAVEN_PROJECTBASEDIR=%~dp0"
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"
set "WRAPPER_DIR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper"
set "WRAPPER_PROPS=%WRAPPER_DIR%\maven-wrapper.properties"

if not exist "%WRAPPER_PROPS%" (
  echo Missing Maven Wrapper properties: %WRAPPER_PROPS% 1>&2
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("%WRAPPER_PROPS%") do (
  if "%%A"=="distributionUrl" set "DISTRIBUTION_URL=%%B"
  if "%%A"=="distributionSha512Sum" set "DISTRIBUTION_SHA512=%%B"
  if "%%A"=="mavenVersion" set "MAVEN_VERSION=%%B"
)

set "MAVEN_HOME_DIR="
if defined MAVEN_USER_HOME (
  set "WRAPPER_CACHE=%MAVEN_USER_HOME%\wrapper\dists\apache-maven-%MAVEN_VERSION%"
) else (
  set "WRAPPER_CACHE=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
)

if exist "%WRAPPER_CACHE%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd" (
  set "MAVEN_HOME_DIR=%WRAPPER_CACHE%\apache-maven-%MAVEN_VERSION%"
  goto run_maven
)

for /f "tokens=3" %%V in ('mvn -v 2^>nul ^| findstr /R /C:"Apache Maven"') do (
  if "%%V"=="%MAVEN_VERSION%" (
    echo Using globally installed Apache Maven %MAVEN_VERSION% because the wrapper distribution is not cached. 1>&2
    mvn %*
    exit /b !ERRORLEVEL!
  )
)

if not defined DISTRIBUTION_URL (
  echo distributionUrl is missing in %WRAPPER_PROPS% 1>&2
  exit /b 1
)

set "ARCHIVE=%WRAPPER_CACHE%\apache-maven-%MAVEN_VERSION%-bin.zip"
mkdir "%WRAPPER_CACHE%" >nul 2>nul
if errorlevel 1 (
  echo Could not create Maven Wrapper cache: %WRAPPER_CACHE% 1>&2
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$archive = '%ARCHIVE%';" ^
  "$cache = '%WRAPPER_CACHE%';" ^
  "$url = '%DISTRIBUTION_URL%';" ^
  "$expected = '%DISTRIBUTION_SHA512%';" ^
  "Invoke-WebRequest -Uri $url -OutFile $archive;" ^
  "if ($expected) { $actual = (Get-FileHash -Algorithm SHA512 $archive).Hash.ToLowerInvariant(); if ($actual -ne $expected.ToLowerInvariant()) { throw 'Maven distribution checksum mismatch.' } }" ^
  "Expand-Archive -Path $archive -DestinationPath $cache -Force;"
if errorlevel 1 (
  echo Failed to download or extract Apache Maven %MAVEN_VERSION%. 1>&2
  exit /b 1
)

set "MAVEN_HOME_DIR=%WRAPPER_CACHE%\apache-maven-%MAVEN_VERSION%"

:run_maven
set "MAVEN_CMD=%MAVEN_HOME_DIR%\bin\mvn.cmd"
if not exist "%MAVEN_CMD%" (
  echo Maven executable not found: %MAVEN_CMD% 1>&2
  exit /b 1
)

"%MAVEN_CMD%" %*
exit /b %ERRORLEVEL%

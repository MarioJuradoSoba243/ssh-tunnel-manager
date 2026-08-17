<# : batch portion
@echo off
@setlocal
@set "__MVNW_ARG0_NAME__=%~nx0"
@set "__MVNW_CMD__="
@set "__MVNW_PSMODULEP_SAVE=%PSModulePath%"
@set "PSModulePath="
@for /f "usebackq tokens=1* delims==" %%A in (`powershell -noprofile "& {$scriptDir='%~dp0'; $script='%__MVNW_ARG0_NAME__%'; icm -ScriptBlock ([Scriptblock]::Create((Get-Content -Raw '%~f0'))) -NoNewScope}"`) do @(
  @if "%%A"=="MVN_CMD" (set "__MVNW_CMD__=%%B") else if "%%B"=="" (echo %%A) else (echo %%A=%%B)
)
@set "PSModulePath=%__MVNW_PSMODULEP_SAVE%"
@set "__MVNW_PSMODULEP_SAVE="
@if not "%__MVNW_CMD__%"=="" (call "%__MVNW_CMD__%" %*)
@exit /b %ERRORLEVEL%
: end batch / begin powershell #>

$ErrorActionPreference = "Stop"
$properties = ConvertFrom-StringData (Get-Content -Raw "$scriptDir/.mvn/wrapper/maven-wrapper.properties")
$distributionUrl = $properties.distributionUrl
if (-not $distributionUrl) { throw "No se puede leer distributionUrl" }

$distributionName = $distributionUrl -replace '^.*/', ''
$distributionBaseName = $distributionName -replace '\.[^.]*$', '' -replace '-bin$', ''
$mavenUserHome = if ($env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME } else { Join-Path $HOME '.m2' }
$hash = ([System.Security.Cryptography.SHA256]::Create().ComputeHash([byte[]][char[]]$distributionUrl) | ForEach-Object { $_.ToString('x2') }) -join ''
$mavenHome = Join-Path (Join-Path (Join-Path $mavenUserHome 'wrapper') 'dists') "$distributionBaseName/$hash"

if (-not (Test-Path (Join-Path $mavenHome 'bin/mvn.cmd'))) {
    $temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("maven-wrapper-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $temporaryDirectory -Force | Out-Null
    try {
        $archive = Join-Path $temporaryDirectory $distributionName
        Invoke-WebRequest -Uri $distributionUrl -OutFile $archive
        Expand-Archive -LiteralPath $archive -DestinationPath $temporaryDirectory
        $extractedDirectory = Get-ChildItem -LiteralPath $temporaryDirectory -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin/mvn.cmd') } | Select-Object -First 1
        if (-not $extractedDirectory) { throw "No se encontró una distribución Maven válida" }
        New-Item -ItemType Directory -Path (Split-Path $mavenHome) -Force | Out-Null
        Move-Item -LiteralPath $extractedDirectory.FullName -Destination $mavenHome
    } finally {
        if (Test-Path $temporaryDirectory) { Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force }
    }
}

Write-Output "MVN_CMD=$(Join-Path $mavenHome 'bin/mvn.cmd')"

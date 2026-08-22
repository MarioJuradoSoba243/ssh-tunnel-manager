param(
    [ValidateSet('image', 'installer', 'portable', 'all')]
    [string] $Type = 'all'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$version = '0.4.0'
$appName = 'ssh-tunnel-manager'
$targetDirectory = Join-Path $projectRoot 'target'
$inputDirectory = Join-Path $targetDirectory 'package-input'
$distDirectory = Join-Path $projectRoot 'dist'
$generatedImageDirectory = Join-Path $distDirectory $appName
$imageDirectory = Join-Path $distDirectory "$appName-$version-windows-x64"
$jarPath = Join-Path $targetDirectory "$appName-$version.jar"

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw 'jpackage no está disponible. Ejecuta este script con un JDK 25 en PATH.'
}
$jpackageVersion = (& jpackage --version | Select-Object -First 1).Trim()
if ($jpackageVersion -notmatch '^25\.') {
    throw "jpackage requiere JDK 25; se ha encontrado la versión $jpackageVersion."
}

if (Test-Path $inputDirectory) {
    Remove-Item -LiteralPath $inputDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $inputDirectory | Out-Null

& (Join-Path $projectRoot 'mvnw.cmd') -B -DskipTests package
if ($LASTEXITCODE -ne 0) {
    throw 'El build Maven ha fallado.'
}
Copy-Item -LiteralPath $jarPath -Destination $inputDirectory -Force

if (Test-Path $distDirectory) {
    Remove-Item -LiteralPath $distDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $distDirectory | Out-Null

function New-ApplicationImage {
    if (Test-Path $generatedImageDirectory) {
        Remove-Item -LiteralPath $generatedImageDirectory -Recurse -Force
    }
    if (Test-Path $imageDirectory) {
        Remove-Item -LiteralPath $imageDirectory -Recurse -Force
    }
    & jpackage `
        --type app-image `
        --name $appName `
        --app-version $version `
        --input $inputDirectory `
        --main-jar (Split-Path $jarPath -Leaf) `
        --main-class com.ssh.tunnelmanager.ui.ApplicationLauncher `
        --dest $distDirectory `
        --java-options '-Dfile.encoding=UTF-8'
    if ($LASTEXITCODE -ne 0) {
        throw 'jpackage no ha podido generar la application image.'
    }
    Move-Item -LiteralPath $generatedImageDirectory -Destination $imageDirectory
}

if ($Type -in @('image', 'installer', 'portable', 'all')) {
    New-ApplicationImage
}

if ($Type -in @('installer', 'all')) {
    & jpackage `
        --type exe `
        --name $appName `
        --app-version $version `
        --input $inputDirectory `
        --main-jar (Split-Path $jarPath -Leaf) `
        --main-class com.ssh.tunnelmanager.ui.ApplicationLauncher `
        --dest $distDirectory `
        --win-dir-chooser `
        --win-per-user-install `
        --win-menu `
        --win-shortcut `
        --java-options '-Dfile.encoding=UTF-8'
    if ($LASTEXITCODE -ne 0) {
        throw 'jpackage no ha podido generar el instalador Windows.'
    }
    $generatedInstaller = Join-Path $distDirectory "$appName-$version.exe"
    $installerPath = Join-Path $distDirectory "$appName-$version-windows-x64-installer.exe"
    if (Test-Path $installerPath) {
        Remove-Item -LiteralPath $installerPath -Force
    }
    Move-Item -LiteralPath $generatedInstaller -Destination $installerPath
}

if ($Type -in @('portable', 'all')) {
    $portableFlag = Join-Path $imageDirectory 'portable.flag'
    New-Item -ItemType File -Path $portableFlag -Force | Out-Null
    foreach ($directory in @('data', 'data\backups', 'data\logs')) {
        New-Item -ItemType Directory -Path (Join-Path $imageDirectory $directory) -Force | Out-Null
    }
    $portableZip = Join-Path $distDirectory "$appName-$version-windows-x64-portable.zip"
    if (Test-Path $portableZip) {
        Remove-Item -LiteralPath $portableZip -Force
    }
    Compress-Archive -Path (Join-Path $imageDirectory '*') -DestinationPath $portableZip
}

Write-Host "Artefactos generados en $distDirectory"

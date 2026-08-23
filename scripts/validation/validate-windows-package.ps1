param(
    [string] $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')),
    [switch] $Installer,
    [switch] $Uninstall
)

$ErrorActionPreference = 'Stop'
$version = '1.0.0'
$appName = 'ssh-tunnel-manager'
$dist = Join-Path $RepositoryRoot 'dist'
$image = Join-Path $dist "$appName-$version-windows-x64"
$portableZip = Join-Path $dist "$appName-$version-windows-x64-portable.zip"
$installerPath = Join-Path $dist "$appName-$version-windows-x64-installer.exe"

function Assert-Path([string] $path, [string] $description) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "No existe $description`: $path"
    }
}

Assert-Path $image 'la application image'
Assert-Path $portableZip 'el ZIP portable'
Assert-Path (Join-Path $image "$appName.exe") 'el ejecutable de la application image'
Assert-Path (Join-Path $image 'runtime\bin\java.dll') 'el runtime incluido'
Assert-Path (Join-Path $image 'runtime\release') 'la metadata del runtime incluido'

$portableRoot = Join-Path $env:TEMP "$appName-portable-validation-$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Path $portableRoot | Out-Null
try {
    Expand-Archive -LiteralPath $portableZip -DestinationPath $portableRoot -Force
    $portableImage = Join-Path $portableRoot "$appName-$version-windows-x64"
    if (-not (Test-Path -LiteralPath $portableImage)) {
        $portableImage = $portableRoot
    }
    $portableExe = Join-Path $portableImage "$appName.exe"
    Assert-Path $portableExe 'el ejecutable portable extraído'
    Assert-Path (Join-Path $portableImage 'runtime\bin\java.dll') 'el runtime portable incluido'

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $portableExe
    $psi.WorkingDirectory = Split-Path -Parent $portableExe
    $psi.UseShellExecute = $false
    $psi.Environment['JAVA_HOME'] = ''
    $psi.Environment['JDK_HOME'] = ''
    $psi.Environment['PATH'] = 'C:\Windows\System32;C:\Windows'
    $process = [System.Diagnostics.Process]::Start($psi)
    Start-Sleep -Seconds 5
    if ($process.HasExited) {
        throw "El portable terminó durante el arranque con código $($process.ExitCode)."
    }
    $data = Join-Path $portableImage 'data'
    Assert-Path (Join-Path $data 'ssh-tunnel-manager.db') 'la base SQLite portable'
    $process.CloseMainWindow() | Out-Null
    if (-not $process.WaitForExit(10000)) {
        $process.Kill()
        $process.WaitForExit(5000) | Out-Null
    }
    if (-not $process.HasExited) {
        $process.Kill()
        $process.WaitForExit(5000) | Out-Null
    }
    if (-not (Test-Path -LiteralPath (Join-Path $data 'ssh-tunnel-manager.db'))) {
        throw 'La configuración portable no sobrevivió al cierre.'
    }
    Write-Host 'PASS portable: runtime, arranque, rutas y persistencia básica.'
}
finally {
    if (Test-Path -LiteralPath $portableRoot) {
        for ($attempt = 0; $attempt -lt 10; $attempt++) {
            try {
                Remove-Item -LiteralPath $portableRoot -Recurse -Force -ErrorAction Stop
                break
            } catch {
                if ($attempt -eq 9) { throw }
                Start-Sleep -Milliseconds 500
            }
        }
    }
}

if ($Installer) {
    Assert-Path $installerPath 'el instalador Windows'
    $install = Start-Process -FilePath $installerPath -ArgumentList @('/quiet', '/norestart') -Wait -PassThru
    if ($install.ExitCode -ne 0) {
        throw "El instalador terminó con código $($install.ExitCode). Ejecuta esta parte en una máquina limpia."
    }
    $installedExe = Join-Path $env:LOCALAPPDATA "$appName\$appName.exe"
    Assert-Path $installedExe 'el ejecutable instalado per-user'
    Write-Host "PASS installer: ejecutable instalado en $installedExe"
    if ($Uninstall) {
        $uninstall = Start-Process -FilePath $installerPath -ArgumentList @('/uninstall', '/quiet', '/norestart') -PassThru
        if (-not $uninstall.WaitForExit(30000)) {
            $uninstall.Kill()
            throw 'La desinstalación no terminó en 30 segundos.'
        }
        if ($uninstall.ExitCode -ne 0) {
            throw "La desinstalación terminó con código $($uninstall.ExitCode)."
        }
        if (Test-Path -LiteralPath $installedExe) {
            throw "El ejecutable instalado sigue presente después de desinstalar: $installedExe"
        }
        Write-Host 'PASS uninstall: el ejecutable instalado ha desaparecido.'
    } else {
        Write-Host 'INFO: usa -Uninstall en una máquina limpia para comprobar la desinstalación.'
    }
}

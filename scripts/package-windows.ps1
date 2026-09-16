param(
    [ValidateSet("app-image", "installer", "portable", "all")]
    [string]$Target = "all"
)

$ErrorActionPreference = "Stop"
$Version = "0.1.0-SNAPSHOT"
$Output = Join-Path $PSScriptRoot "..\target\distribution"
$ImageName = "SSH Tunnel Manager"
$ImagePath = Join-Path $Output $ImageName

& "$PSScriptRoot\..\mvnw.cmd" -B package dependency:copy-dependencies `
    "-DincludeScope=runtime" "-DoutputDirectory=target/package-libs"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

New-Item -ItemType Directory -Force -Path $Output | Out-Null

function New-AppImage {
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $ImagePath
    jpackage --type app-image --name $ImageName --app-version "0.1.0" `
        --input "target\package-input" --main-jar "ssh-tunnel-manager-$Version.jar" `
        --main-class "com.sshtunnelmanager.Launcher" `
        --dest $Output --win-console
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Remove-Item -Recurse -Force -ErrorAction SilentlyContinue "target\package-input"
New-Item -ItemType Directory -Force -Path "target\package-input" | Out-Null
Copy-Item "target\ssh-tunnel-manager-$Version.jar" "target\package-input\"
Copy-Item "target\package-libs\*.jar" "target\package-input\"

if ($Target -in @("app-image", "portable", "all")) { New-AppImage }
if ($Target -in @("installer", "all")) {
    jpackage --type exe --name $ImageName --app-version "0.1.0" `
        --input "target\package-input" --main-jar "ssh-tunnel-manager-$Version.jar" `
        --main-class "com.sshtunnelmanager.Launcher" `
        --dest $Output --win-dir-chooser --win-menu --win-shortcut
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
if ($Target -in @("portable", "all")) {
    Compress-Archive -Force -Path $ImagePath `
        -DestinationPath (Join-Path $Output "ssh-tunnel-manager-0.1.0-windows-x64-portable.zip")
}

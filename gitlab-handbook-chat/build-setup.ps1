param (
    [string]$Type = "msi",
    [string]$ServerIP = "localhost"
)

$ErrorActionPreference = "Stop"

$appName = "SecretChat"

function Add-PortableDesktopShortcutScripts {
    param (
        [Parameter(Mandatory = $true)]
        [string]$AppDir
    )

    $psScriptPath = Join-Path $AppDir "Create-Desktop-Shortcut.ps1"
    $cmdScriptPath = Join-Path $AppDir "Create-Desktop-Shortcut.cmd"

    @'
$ErrorActionPreference = "Stop"

$appDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$exePath = Join-Path $appDir "SecretChat.exe"
if (-not (Test-Path $exePath)) {
    throw "Khong tim thay SecretChat.exe trong thu muc ung dung."
}

$desktopPath = [Environment]::GetFolderPath("Desktop")
$shortcutPath = Join-Path $desktopPath "SecretChat.lnk"

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $exePath
$shortcut.WorkingDirectory = $appDir
$shortcut.IconLocation = "$exePath,0"
$shortcut.Description = "SecretChat"
$shortcut.Save()

Write-Host "Da tao shortcut SecretChat tren Desktop: $shortcutPath"
'@ | Set-Content -Path $psScriptPath -Encoding UTF8

    @'
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Create-Desktop-Shortcut.ps1"
pause
'@ | Set-Content -Path $cmdScriptPath -Encoding ASCII
}

if (-not $env:JAVA_HOME) {
    $candidate = Get-ChildItem "$env:USERPROFILE\.jdks\*" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "*21*" } |
        Select-Object -First 1
    if ($candidate) {
        $env:JAVA_HOME = $candidate.FullName
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    throw "Khong tim thay JDK 21. Hay thiet lap JAVA_HOME."
}

$wixInstalled = (Get-Command "candle.exe" -ErrorAction SilentlyContinue) -and
    (Get-Command "light.exe" -ErrorAction SilentlyContinue)
$buildZip = $false
if (($Type -eq "msi" -or $Type -eq "exe") -and -not $wixInstalled) {
    $Type = "app-image"
    $buildZip = $true
}

$configPath = Join-Path $PSScriptRoot "src\main\resources\gateway-config.properties"
$originalConfig = Get-Content -Path $configPath -Raw

try {
    @"
gateway.host=$ServerIP
gateway.port=8088
gateway.scheme=http
"@ | Set-Content -Path $configPath

    & (Join-Path $PSScriptRoot "mvnw.cmd") clean compile javafx:jlink
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed."
    }

    $destDir = Join-Path $PSScriptRoot "dist"
    if (Test-Path $destDir) {
        Remove-Item -LiteralPath $destDir -Recurse -Force
    }

    $jpackageArgs = @(
        "--name", $appName,
        "--vendor", $appName,
        "--app-version", "1.0.0",
        "--runtime-image", (Join-Path $PSScriptRoot "target\app"),
        "--module", "secretchat.secrectchat/secretchat.ChatApplication",
        "--dest", $destDir,
        "--type", $Type
    )
    if ($Type -eq "msi" -or $Type -eq "exe") {
        $jpackageArgs += @("--win-dir-chooser", "--win-menu", "--win-shortcut")
    }

    & (Join-Path $env:JAVA_HOME "bin\jpackage.exe") $jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed."
    }

    if ($buildZip -or $Type -eq "app-image") {
        $appDir = Join-Path $destDir $appName
        Add-PortableDesktopShortcutScripts -AppDir $appDir
        Compress-Archive -Path $appDir `
            -DestinationPath (Join-Path $destDir "$appName-portable.zip") -Force
    }
} finally {
    [System.IO.File]::WriteAllText($configPath, $originalConfig)
}

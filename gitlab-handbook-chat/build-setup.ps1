param (
    [string]$Type = "msi",
    [string]$ServerIP = "localhost"
)

$ErrorActionPreference = "Stop"

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
        "--name", "SecretChat",
        "--vendor", "SecretChat",
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
        Compress-Archive -Path (Join-Path $destDir "SecretChat") `
            -DestinationPath (Join-Path $destDir "SecretChat-portable.zip") -Force
    }
} finally {
    Set-Content -Path $configPath -Value $originalConfig
}

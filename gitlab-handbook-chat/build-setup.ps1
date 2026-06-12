param (
    [string]$Type = "msi", # Kieu dong goi: msi, exe, app-image
    [string]$CaPath = "..\chat-system\secrets\tls\mail\ca.pem", # Duong dan den file CA
    [string]$ServerIP = "localhost" # IP LAN hoac hostname cua Server de client tu dong ket noi
)

$ErrorActionPreference = "Stop"

Write-Host "=== BAT DAU QUY TRINH DONG GOI SECRET CHAT ==="

# 1. Kiem tra va thiet lap JAVA_HOME
if (-not $env:JAVA_HOME) {
    Write-Host "Khong tim thay bien JAVA_HOME, dang tim kiem JDK 21 tu dong..."
    $userProfile = $env:USERPROFILE
    $possiblePaths = @(
        "$userProfile\.jdks\microsoft-jdk-21\jdk-21.0.11+10",
        (Get-ChildItem "$userProfile\.jdks\*" -Directory 2>$null | Where-Object { $_.Name -like "*21*" } | Select-Object -First 1 | ForEach-Object { $_.FullName })
    )
    foreach ($path in $possiblePaths) {
        if ($path -and (Test-Path $path)) {
            $env:JAVA_HOME = $path
            break
        }
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    Write-Error "Khong tim thay JDK. Vui long thiet lap bien moi truong JAVA_HOME tro den JDK 21."
    exit 1
}

Write-Host "Su dung JDK tai: $env:JAVA_HOME"

# 2. Xac dinh duong dan file CA va bien moi truong
$caFile = Join-Path $PSScriptRoot $CaPath
$caFile = [System.IO.Path]::GetFullPath($caFile)

if (-not (Test-Path $caFile)) {
    Write-Error "Khong tim thay file CA tai: $caFile. Vui long kiem tra lai duong dan."
    exit 1
}
Write-Host "Tim thay file CA tai: $caFile"

# 3. Kiem tra WiX Toolset neu build file setup (.msi/.exe)
$wixInstalled = $false
$candle = Get-Command "candle.exe" -ErrorAction SilentlyContinue
$light = Get-Command "light.exe" -ErrorAction SilentlyContinue
if ($candle -and $light) {
    $wixInstalled = $true
} else {
    $wixPaths = @()
    # Tim kiem cac thu muc WiX Toolset v3.* tu dong
    $wixFolders = Get-ChildItem -Path "C:\Program Files (x86)", "C:\Program Files" -Filter "WiX Toolset v3*" -Directory -ErrorAction SilentlyContinue
    foreach ($folder in $wixFolders) {
        $wixPaths += Join-Path $folder.FullName "bin"
    }
    # Cac duong dan mac dinh bo sung
    $wixPaths += @(
        "C:\Program Files (x86)\WiX Toolset v3.14\bin",
        "C:\Program Files\WiX Toolset v3.14\bin",
        "C:\Program Files (x86)\WiX Toolset v3.11\bin",
        "C:\Program Files\WiX Toolset v3.11\bin",
        "$env:USERPROFILE\scoop\apps\wix\current\bin"
    )
    foreach ($path in $wixPaths) {
        if (Test-Path $path) {
            $env:PATH += ";$path"
            $wixInstalled = $true
            break
        }
    }
}

$BuildZip = $false
if (($Type -eq "msi" -or $Type -eq "exe") -and -not $wixInstalled) {
    Write-Warning "Khong tim thay WiX Toolset v3 (can thiet de build file setup .msi/.exe)."
    Write-Host "-> De build msi, ban co the cai dat WiX bang cach chay: choco install wixtoolset"
    Write-Host "-> Tam thoi chuyen sang che do build thu muc chay ngay (Portable ZIP) khong can WiX..."
    $Type = "app-image"
    $BuildZip = $true
}

$embeddedCaDest = Join-Path $PSScriptRoot "src\main\resources\secretchat-ca.pem"
$configPropsPath = Join-Path $PSScriptRoot "src\main\resources\gateway-config.properties"
$originalConfigProps = ""

try {
    # 3.5. Nhung chung chi CA va cau hinh IP Server mac dinh vao resources truoc khi compile
    if (Test-Path $caFile) {
        Write-Host "Dang nhung chung chi CA vao resources..."
        Copy-Item -Path $caFile -Destination $embeddedCaDest -Force
    }

    if (Test-Path $configPropsPath) {
        $originalConfigProps = Get-Content -Path $configPropsPath -Raw
    }
    $newConfigContent = @"
gateway.host=$ServerIP
gateway.port=8088
gateway.scheme=https
"@
    Set-Content -Path $configPropsPath -Value $newConfigContent
    Write-Host "Da cau hinh default gateway IP trong packaged app la: $ServerIP"

    # 4. Build du an va sinh ra runtime JRE bang Maven
    Write-Host "Dang build va sinh JRE tu bien (jlink) qua Maven..."
    $mvnw = Join-Path $PSScriptRoot "mvnw.cmd"
    & $mvnw clean compile javafx:jlink

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Loi khi chay build Maven!"
        exit 1
    }

    # 5. Import chung chi CA vao JRE truststore cua target/app (cho cac truong hop keo runtime rieng)
    $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    $cacerts = Join-Path $PSScriptRoot "target\app\lib\security\cacerts"

    if (-not (Test-Path $cacerts)) {
        Write-Error "Khong tim thay file cacerts trong JRE tai: $cacerts"
        exit 1
    }

    Write-Host "Dang import chung chi CA vao truststore cua JRE tai: $cacerts"
    # Xoa alias cu neu co san de tránh loi duplicate
    & $keytool -delete -alias "secretchat-local-ca" -keystore $cacerts -storepass "changeit" 2>$null | Out-Null

    # Import chung chi moi
    & $keytool -importcert -noprompt -trustcacerts -alias "secretchat-local-ca" -file $caFile -keystore $cacerts -storepass "changeit"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Import chung chi CA vao JRE gap loi!"
        exit 1
    }
    Write-Host "Da import chung chi CA thanh cong vao runtime JRE."

    # 6. Chay jpackage de dong goi
    $jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    $destDir = Join-Path $PSScriptRoot "dist"

    if (Test-Path $destDir) {
        Write-Host "Dang xoa thu muc dist cu..."
        Remove-Item -Path $destDir -Recurse -Force
    }

    Write-Host "Dang dong goi voi kieu: $Type..."
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
        $jpackageArgs += @(
            "--win-dir-chooser",
            "--win-menu",
            "--win-shortcut"
        )
    }

    & $jpackage $jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Chay jpackage gap loi!"
        exit 1
    }

    # 7. Nen thanh file ZIP neu chay o che do Portable
    if ($BuildZip -or $Type -eq "app-image") {
        Write-Host "Dang nen thu muc ung dung thanh file ZIP di dong..."
        $zipPath = Join-Path $destDir "SecretChat-portable.zip"
        Compress-Archive -Path (Join-Path $destDir "SecretChat") -DestinationPath $zipPath -Force
        Write-Host "File ZIP di dong da duoc tao thanh cong tai: $zipPath"
    }

    Write-Host "=== QUY TRINH HOAN THANH CONG! ==="
    Write-Host "Ket qua luu tai thu muc: $destDir"
} finally {
    # 8. Don dep thiet lap tam thoi de tranh gay ban Git workspace
    Write-Host "Dang don dep cac thiet lap tam thoi trong resources..."
    if (Test-Path $embeddedCaDest) {
        Remove-Item -Path $embeddedCaDest -Force
    }
    if ($originalConfigProps) {
        Set-Content -Path $configPropsPath -Value $originalConfigProps
    }
}

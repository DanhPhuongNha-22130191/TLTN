param (
    [string]$ServerIP = ""
)

$ErrorActionPreference = "Stop"

if (-not $ServerIP) {
    Write-Host "Vui long cung cap IP cua may chu (Server IP)." -ForegroundColor Red
    Write-Host "Vi du: .\generate-keys.ps1 -ServerIP 192.168.1.91"
    exit 1
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$secretsDir = Join-Path $scriptDir "secrets"
$tlsDir = Join-Path $secretsDir "tls"

Write-Host "=== DANG KHOI TAO CHUNG CHI CHO IP: $ServerIP ===" -ForegroundColor Green

# 1. Tao cau hinh openssl.conf tam thoi
$confContent = @"
[req]
default_bits = 2048
prompt = no
default_md = sha256
req_extensions = req_ext
distinguished_name = dn

[dn]
CN = localhost

[req_ext]
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
IP.1 = 127.0.0.1
IP.2 = $ServerIP
DNS.2 = mail.gitlab.handbook.local
"@

$confPath = Join-Path $secretsDir "openssl.conf"
Set-Content -Path $confPath -Value $confContent

Write-Host "Chay OpenSSL qua Docker de tao chung chi (khong can mount, su dung docker cp)..."

# 2. Khoi chay mot container alpine/openssl tam thoi o che do nen
$containerId = (docker run -d --entrypoint sh alpine/openssl -c "tail -f /dev/null").Trim()

try {
    # 3. Copy file cau hinh vao container
    docker cp $confPath "${containerId}:/openssl.conf"

    # 4. Sinh khoa va chung chi trong container
    $commands = @(
        "openssl genrsa -out /ca.key 2048",
        "openssl req -x509 -new -nodes -key /ca.key -sha256 -days 3650 -out /ca.pem -subj '/CN=SecretChat Local CA' -addext 'basicConstraints=critical,CA:true' -addext 'keyUsage=critical,keyCertSign,cRLSign'",
        "openssl genrsa -out /privkey.pem 2048",
        "openssl req -new -key /privkey.pem -out /cert.csr -config /openssl.conf",
        "openssl x509 -req -in /cert.csr -CA /ca.pem -CAkey /ca.key -CAcreateserial -out /fullchain.pem -days 825 -sha256 -extfile /openssl.conf -extensions req_ext"
    )

    foreach ($cmd in $commands) {
        docker exec $containerId sh -c $cmd
    }

    # 5. Copy cac file ket qua ve lai thu muc secrets
    docker cp "${containerId}:/ca.key" (Join-Path $secretsDir "ca.key")
    docker cp "${containerId}:/ca.pem" (Join-Path $secretsDir "ca.pem")
    docker cp "${containerId}:/privkey.pem" (Join-Path $secretsDir "privkey.pem")
    docker cp "${containerId}:/fullchain.pem" (Join-Path $secretsDir "fullchain.pem")
    if (docker exec $containerId test -f /ca.srl) {
        docker cp "${containerId}:/ca.srl" (Join-Path $secretsDir "ca.srl")
    }
}
finally {
    # 6. Xoa container tam thoi
    docker rm -f $containerId | Out-Null
    # Xoa file cau hinh tam thoi
    Remove-Item -Path $confPath -Force -ErrorAction SilentlyContinue
}

Write-Host "Da tao xong chung chi. Dang phan phoi toi cac thu muc..." -ForegroundColor Green

# 7. Phan phoi chung chi den cac thu muc dich vu
$targets = @("gateway", "keycloak", "mail", "webmail")
foreach ($target in $targets) {
    $targetDir = Join-Path $tlsDir $target
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }
    Copy-Item -Path (Join-Path $secretsDir "fullchain.pem") -Destination (Join-Path $targetDir "fullchain.pem") -Force
    Copy-Item -Path (Join-Path $secretsDir "privkey.pem") -Destination (Join-Path $targetDir "privkey.pem") -Force
}

Copy-Item -Path (Join-Path $secretsDir "ca.pem") -Destination (Join-Path $tlsDir "mail\ca.pem") -Force
Copy-Item -Path (Join-Path $secretsDir "ca.key") -Destination (Join-Path $tlsDir "mail\ca.key") -Force
if (Test-Path (Join-Path $secretsDir "ca.srl")) {
    Copy-Item -Path (Join-Path $secretsDir "ca.srl") -Destination (Join-Path $tlsDir "mail\ca.srl") -Force
}

# Don dep cac file goc trong thu muc secrets sau khi phan phoi
Remove-Item -Path (Join-Path $secretsDir "ca.key") -Force -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $secretsDir "ca.pem") -Force -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $secretsDir "ca.srl") -Force -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $secretsDir "privkey.pem") -Force -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $secretsDir "fullchain.pem") -Force -ErrorAction SilentlyContinue

Write-Host "`n=== KET QUA ===" -ForegroundColor Green
Write-Host "1. File ca.pem moi da duoc phan phoi tai: chat-system\secrets\tls\mail\ca.pem"
Write-Host "2. Hay cap nhat PUBLIC_HOSTNAME=<ServerIP> trong file chat-system\.env cua Server."
Write-Host "3. Chay 'docker compose down' va 'docker compose up -d' de tai lai cau hinh va chung chi moi."
Write-Host "4. De dong goi ung dung khách cho nguoi khac tu dong ket noi:"
Write-Host "   Chay script build-setup.ps1 voi IP may chu:"
Write-Host "   .\build-setup.ps1 -ServerIP <ServerIP>"

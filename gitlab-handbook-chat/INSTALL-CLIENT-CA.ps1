$ErrorActionPreference = "Stop"

$appDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$keytool = Join-Path $appDirectory "runtime\bin\keytool.exe"
$truststore = Join-Path $appDirectory "runtime\lib\security\cacerts"
$certificate = Join-Path $appDirectory "secretchat-ca.pem"
$alias = "secretchat-local-ca"

if (-not (Test-Path -LiteralPath $keytool)) {
    throw "Khong tim thay keytool trong runtime cua SecretChat."
}
if (-not (Test-Path -LiteralPath $truststore)) {
    throw "Khong tim thay truststore cua SecretChat."
}
if (-not (Test-Path -LiteralPath $certificate)) {
    throw "Hay dat file secretchat-ca.pem canh SecretChat.exe."
}

$delete = Start-Process -FilePath $keytool -NoNewWindow -Wait -PassThru `
    -ArgumentList @(
        "-delete",
        "-alias", $alias,
        "-cacerts",
        "-storepass", "changeit"
    )

$import = Start-Process -FilePath $keytool -NoNewWindow -Wait -PassThru `
    -ArgumentList @(
        "-importcert",
        "-noprompt",
        "-trustcacerts",
        "-alias", $alias,
        "-file", "`"$certificate`"",
        "-cacerts",
        "-storepass", "changeit"
    )

if ($import.ExitCode -ne 0) {
    throw "Khong the cai CA vao runtime SecretChat."
}

Write-Host "Da cai SecretChat Local CA. Hay dong va mo lai SecretChat.exe."

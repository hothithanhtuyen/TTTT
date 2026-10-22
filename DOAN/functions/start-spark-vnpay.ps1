[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$TunnelOrigin
)

$ErrorActionPreference = 'Stop'
$projectId = 'tttt-cinema-20261020'
$functionPath = "$projectId/asia-southeast1/vnpayApi"
$secretFile = Join-Path $PSScriptRoot '.secret.local'

try {
    $tunnelUri = [Uri]$TunnelOrigin
} catch {
    throw 'TunnelOrigin phải là URL HTTPS hợp lệ, ví dụ https://abc.ngrok-free.app.'
}

if ($tunnelUri.Scheme -ne 'https' -or $tunnelUri.AbsolutePath -ne '/' -or $tunnelUri.Query -or $tunnelUri.Fragment) {
    throw 'Chỉ nhập origin HTTPS của tunnel, không kèm đường dẫn, query hoặc fragment.'
}

if (-not (Test-Path -LiteralPath $secretFile -PathType Leaf)) {
    throw "Thiếu functions/.secret.local. Tạo file này với VNPAY_HASH_SECRET và ADMIN_BOOTSTRAP_CODE; không commit file."
}

$functionBaseUrl = $TunnelOrigin.TrimEnd('/') + '/' + $functionPath
$projectRoot = Split-Path $PSScriptRoot -Parent
$localPropertiesPath = Join-Path $projectRoot 'local.properties'
$localProperties = @()
if (Test-Path -LiteralPath $localPropertiesPath -PathType Leaf) {
    $localProperties = @(Get-Content -LiteralPath $localPropertiesPath | Where-Object { $_ -notmatch '^\s*VNPAY_API_BASE_URL\s*=' })
}
$localProperties += "VNPAY_API_BASE_URL=$functionBaseUrl"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($localPropertiesPath, [string[]]$localProperties, $utf8NoBom)

# Firebase Functions loads parameter values from .env.local. Keep the emulator's
# signed VNPAY return URL on the same tunnel origin that the Android app calls.
$functionsEnvPath = Join-Path $PSScriptRoot '.env.local'
$functionsEnv = @()
if (Test-Path -LiteralPath $functionsEnvPath -PathType Leaf) {
    $functionsEnv = @(Get-Content -LiteralPath $functionsEnvPath | Where-Object {
        $_ -notmatch '^\s*(VNPAY_API_BASE_URL|VNPAY_TMN_CODE)\s*='
    })
}
$functionsEnv += "VNPAY_TMN_CODE=GW923J6D"
$functionsEnv += "VNPAY_API_BASE_URL=$functionBaseUrl"
[System.IO.File]::WriteAllLines($functionsEnvPath, [string[]]$functionsEnv, $utf8NoBom)

$previousApiBaseUrl = $env:VNPAY_API_BASE_URL
$previousTmnCode = $env:VNPAY_TMN_CODE
$env:VNPAY_API_BASE_URL = $functionBaseUrl
$env:VNPAY_TMN_CODE = 'GW923J6D'

Write-Host "Project Firebase: $projectId"
Write-Host "Backend API:      $functionBaseUrl"
Write-Host "VNPAY IPN URL:    $functionBaseUrl/api/payments/vnpay/ipn"
Write-Host 'Đã lưu Backend API vào local.properties để Android Studio dùng khi Run.'
Write-Host 'Đã cập nhật functions/.env.local để VNPAY quay lại đúng tunnel.'
Write-Host 'Đang chạy Functions Emulator; giữ terminal và tunnel mở trong lúc thử thanh toán.'

Push-Location (Join-Path $PSScriptRoot '..')
try {
    & npx.cmd --yes firebase-tools@15.34.0 emulators:start --only functions --project $projectId
    if ($LASTEXITCODE -ne 0) {
        throw "Firebase Emulator thoát với mã lỗi $LASTEXITCODE."
    }
} finally {
    Pop-Location
    if ($null -eq $previousApiBaseUrl) {
        Remove-Item Env:VNPAY_API_BASE_URL -ErrorAction SilentlyContinue
    } else {
        $env:VNPAY_API_BASE_URL = $previousApiBaseUrl
    }
    if ($null -eq $previousTmnCode) {
        Remove-Item Env:VNPAY_TMN_CODE -ErrorAction SilentlyContinue
    } else {
        $env:VNPAY_TMN_CODE = $previousTmnCode
    }
}

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
$previousApiBaseUrl = $env:VNPAY_API_BASE_URL
$previousTmnCode = $env:VNPAY_TMN_CODE
$env:VNPAY_API_BASE_URL = $functionBaseUrl
$env:VNPAY_TMN_CODE = 'GW923J6D'

Write-Host "Project Firebase: $projectId"
Write-Host "Backend API:      $functionBaseUrl"
Write-Host "VNPAY IPN URL:    $functionBaseUrl/api/payments/vnpay/ipn"
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

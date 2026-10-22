[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$projectId = 'tttt-cinema-20261020'
$functionPath = "$projectId/asia-southeast1/vnpayApi"
$secretFile = Join-Path $PSScriptRoot '.secret.local'
$cloudflaredLog = Join-Path $PSScriptRoot '.cloudflared.log'
$localPropertiesPath = Join-Path $projectRoot 'local.properties'
$functionsEnvPath = Join-Path $PSScriptRoot '.env.local'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# Ensure required tools are in PATH
$knownPaths = @(
    "C:\Users\admin\AppData\Local\Programs\nodejs",
    "C:\Users\admin\AppData\Local\Programs\cloudflared",
    "$env:LOCALAPPDATA\Google\Cloud SDK\google-cloud-sdk\bin",
    "C:\Program Files\Android\Android Studio\jbr\bin"
)
foreach ($p in $knownPaths) {
    if ((Test-Path $p) -and ($env:PATH -notlike "*$p*")) {
        $env:PATH = "$p;$env:PATH"
    }
}
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}

function Test-BackendHealth {
    $conn = Get-NetTCPConnection -LocalPort 5001 -State Listen -ErrorAction SilentlyContinue
    if (-not $conn) { return $false }
    try {
        $res = Invoke-WebRequest -Uri "http://127.0.0.1:5001/$functionPath/api/payments/vnpay/ipn" -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
        return ($res.StatusCode -eq 200 -or $res.StatusCode -eq 400)
    } catch {
        # Functions emulator returns 200 with {"RspCode":"97"} on GET
        if ($_.Exception.Response) { return $true }
        return $false
    }
}

function Test-TunnelRunning {
    $proc = Get-Process cloudflared -ErrorAction SilentlyContinue
    return [bool]$proc
}

function Get-CurrentTunnelOrigin {
    if (Test-Path -LiteralPath $functionsEnvPath -PathType Leaf) {
        $line = Get-Content -LiteralPath $functionsEnvPath | Where-Object { $_ -match '^\s*VNPAY_API_BASE_URL\s*=\s*(https://[^\s/]+)' }
        if ($line -and $matches[1]) {
            return $matches[1]
        }
    }
    if (Test-Path -LiteralPath $localPropertiesPath -PathType Leaf) {
        $line = Get-Content -LiteralPath $localPropertiesPath | Where-Object { $_ -match '^\s*VNPAY_API_BASE_URL\s*=\s*(https://[^\s/]+)' }
        if ($line -and $matches[1]) {
            return $matches[1]
        }
    }
    return $null
}

# 1. Quick check: if both tunnel and backend emulator are healthy
$tunnelOk = Test-TunnelRunning
$backendOk = Test-BackendHealth
$existingOrigin = Get-CurrentTunnelOrigin

if ($tunnelOk -and $backendOk -and $existingOrigin) {
    Write-Host "[VNPAY Backend] Backend và Cloudflare Tunnel đang hoạt động tại $existingOrigin"
    exit 0
}

Write-Host "[VNPAY Backend] Kiểm tra hoặc khởi động lại backend & tunnel..."

if (-not (Test-Path -LiteralPath $secretFile -PathType Leaf)) {
    Write-Warning "Thiếu functions/.secret.local. Vui lòng tạo file với VNPAY_HASH_SECRET."
    exit 1
}

# 2. Ensure cloudflared is running
$tunnelOrigin = $null
if (-not $tunnelOk) {
    Write-Host "[VNPAY Backend] Đang khởi động Cloudflare Quick Tunnel..."
    Remove-Item -LiteralPath $cloudflaredLog -Force -ErrorAction SilentlyContinue
    Start-Process -FilePath "cloudflared" -ArgumentList "tunnel --url http://127.0.0.1:5001" -WindowStyle Hidden -RedirectStandardError $cloudflaredLog
    
    # Wait for URL to appear in log
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 500
        if (Test-Path -LiteralPath $cloudflaredLog -PathType Leaf) {
            $logContent = Get-Content -LiteralPath $cloudflaredLog -Raw -ErrorAction SilentlyContinue
            if ($logContent -match 'https://[a-zA-Z0-9-]+\.trycloudflare\.com') {
                $tunnelOrigin = $matches[0]
                break
            }
        }
    }
    if (-not $tunnelOrigin) {
        Write-Warning "[VNPAY Backend] Không lấy được URL từ Cloudflare Tunnel sau 15 giây."
        exit 1
    }
} else {
    $tunnelOrigin = $existingOrigin
    if (-not $tunnelOrigin) {
        $tunnelOrigin = "https://magnetic-plots-baking-albany.trycloudflare.com"
    }
}

# 3. Update local.properties and functions/.env.local
$functionBaseUrl = $tunnelOrigin.TrimEnd('/') + '/' + $functionPath

$localProperties = @()
if (Test-Path -LiteralPath $localPropertiesPath -PathType Leaf) {
    $localProperties = @(Get-Content -LiteralPath $localPropertiesPath | Where-Object { $_ -notmatch '^\s*VNPAY_API_BASE_URL\s*=' })
}
$localProperties += "VNPAY_API_BASE_URL=$functionBaseUrl"
[System.IO.File]::WriteAllLines($localPropertiesPath, [string[]]$localProperties, $utf8NoBom)

$functionsEnv = @()
if (Test-Path -LiteralPath $functionsEnvPath -PathType Leaf) {
    $functionsEnv = @(Get-Content -LiteralPath $functionsEnvPath | Where-Object {
        $_ -notmatch '^\s*(VNPAY_API_BASE_URL|VNPAY_TMN_CODE)\s*='
    })
}
$functionsEnv += "VNPAY_TMN_CODE=GW923J6D"
$functionsEnv += "VNPAY_API_BASE_URL=$functionBaseUrl"
[System.IO.File]::WriteAllLines($functionsEnvPath, [string[]]$functionsEnv, $utf8NoBom)

# 4. Ensure Functions Emulator is running
if (-not (Test-BackendHealth)) {
    Write-Host "[VNPAY Backend] Đang khởi động Firebase Functions Emulator..."
    $startCmd = "`$env:JAVA_HOME = '$($env:JAVA_HOME)'; `$env:PATH = '$($env:PATH)'; cd `"$projectRoot`"; npx.cmd --yes firebase-tools@15.34.0 emulators:start --only functions --project $projectId"
    Start-Process powershell.exe -ArgumentList "-NoExit -ExecutionPolicy Bypass -Command `"$startCmd`"" -WindowStyle Minimized

    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        if (Test-BackendHealth) {
            Write-Host "[VNPAY Backend] Functions Emulator đã sẵn sàng!"
            break
        }
    }
}

Write-Host "[VNPAY Backend] Sẵn sàng tại: $functionBaseUrl"
exit 0

param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$MediaUrl = 'http://localhost:8088',
    [string]$Token = ''
)

# Gateway smoke test. Proves the edge actually routes each path family to its
# owner, hides the internal surface, keeps the CSRF cookie, and does not buffer
# server-sent events. It does not need users or listings in the database.
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (-not $Token) {
    $envFile = Join-Path $repo '.env'
    if (-not (Test-Path $envFile)) { throw 'Provide -Token or create .env from .env.example.' }
    $line = Get-Content $envFile | Where-Object { $_ -match '^INTERNAL_API_TOKEN=' } | Select-Object -First 1
    if (-not $line) { throw 'INTERNAL_API_TOKEN is missing from .env.' }
    $Token = $line -replace '^INTERNAL_API_TOKEN=', ''
}

$work = Join-Path $env:LOCALAPPDATA ("Temp/opencode/gateway-smoke-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Force $work | Out-Null
$body = Join-Path $work 'body.txt'
$headers = Join-Path $work 'headers.txt'
$png = Join-Path $work 'sample.png'
$oversize = Join-Path $work 'oversize.bin'
$failed = @()

Add-Type -AssemblyName System.Drawing
$bitmap = New-Object System.Drawing.Bitmap 8, 8
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.Clear([System.Drawing.Color]::SeaGreen)
$graphics.Dispose()
$bitmap.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()

# 9 MB, above the edge's 8 MB ceiling, so nginx must answer 413 itself.
$stream = [System.IO.File]::OpenWrite($oversize)
$stream.SetLength(9MB)
$stream.Close()

function Get-Status([string[]]$Arguments) {
    & curl.exe -sS -o $body -w '%{http_code}' @Arguments
}

function Assert-Throws {
    param([string]$Name, [scriptblock]$Check)
    try { & $Check; Write-Output "PASS  $Name" }
    catch { Write-Output "FAIL  $Name -> $($_.Exception.Message)"; $script:failed += $Name }
}

try {
    Assert-Throws 'edge health 200' {
        $code = Get-Status @("$BaseUrl/healthz")
        if ($code -ne '200') { throw "got $code" }
    }

    Assert-Throws 'page routing reaches frontend' {
        & curl.exe -sS -o $body "$BaseUrl/" | Out-Null
        $html = Get-Content $body -Raw
        if ($html -notmatch 'Used|Marketplace|<html') { throw 'response is not the frontend page' }
    }

    Assert-Throws 'api prefixes reach their owner, not the frontend' {
        $routes = @(
            '/api/profile',
            '/api/listings/mine',
            '/api/favorites/1/check',
            '/api/notifications/unread-count',
            '/api/admin/reports',
            '/api/chat/conversations',
            '/api/wallet'
        )
        foreach ($route in $routes) {
            $code = Get-Status @("$BaseUrl$route")
            if ($code -eq '404') { throw "$route returned 404 (frontend fallback)" }
            if ($code -notin @('200', '401', '403')) { throw "$route returned $code" }
        }
    }

    Assert-Throws 'csrf cookie survives the edge' {
        & curl.exe -sS -D $headers -o $body "$BaseUrl/auth/user/csrf" | Out-Null
        $raw = Get-Content $headers -Raw
        if ($raw -notmatch '200') { throw 'csrf endpoint did not return 200' }
        if ($raw -notmatch 'used_csrf') { throw 'Set-Cookie used_csrf not present' }
    }

    Assert-Throws 'internal api is blocked' {
        $code = Get-Status @("$BaseUrl/api/internal/users/1")
        if ($code -ne '404') { throw "got $code, expected 404" }
    }

    Assert-Throws 'actuator is blocked' {
        $code = Get-Status @("$BaseUrl/actuator/health")
        if ($code -ne '404') { throw "got $code, expected 404" }
    }

    Assert-Throws 'oversized body is rejected at the edge' {
        $code = Get-Status @('-X', 'POST', '-H', 'Content-Type: application/json',
            "--data-binary", "@$oversize", "$BaseUrl/auth/user/login")
        if ($code -ne '413') { throw "got $code, expected 413" }
    }

    Assert-Throws 'sse endpoint is routed and not buffered' {
        $code = Get-Status @('--max-time', '20', "$BaseUrl/api/notifications/stream")
        if ($code -eq '000') { throw 'timed out (buffered or unrouted)' }
        if ($code -notin @('200', '401')) { throw "unexpected $code" }
    }

    Assert-Throws 'media bytes pass through the edge' {
        $create = Get-Status @('-X', 'POST', "$MediaUrl/api/internal/media",
            '-H', "X-Internal-Token: $Token", '-F', 'ownerId=1', '-F', "file=@$png")
        if ($create -ne '201') { throw "direct upload got $create" }
        $upload = Get-Content $body -Raw | ConvertFrom-Json
        $id = $upload.id
        & curl.exe -sS -D $headers -o $body "$BaseUrl/api/media/$id" | Out-Null
        $raw = Get-Content $headers -Raw
        if ($raw -notmatch '200') { throw 'retrieval did not return 200' }
        if ($raw -notmatch 'image/png') { throw 'content type is not image/png' }
        $expected = (Get-FileHash $png -Algorithm SHA256).Hash
        $actual = (Get-FileHash $body -Algorithm SHA256).Hash
        if ($expected -ne $actual) { throw 'bytes differ from the uploaded image' }
    }

    if ($failed.Count -gt 0) { throw ("FAILED: " + ($failed -join ', ')) }
    Write-Output 'GATEWAY_SMOKE_OK health page api csrf internal-blocked actuator-blocked body-limit sse media'
} finally {
    Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
}

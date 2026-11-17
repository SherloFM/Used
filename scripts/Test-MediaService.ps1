param(
    [string]$BaseUrl = 'http://localhost:8088',
    [string]$Token = ''
)

# Direct Media smoke test: health, token enforcement, validation, upload,
# retrieval, caching, and 304. It does not need users or listings.
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (-not $Token) {
    if (-not (Test-Path (Join-Path $repo '.env'))) {
        throw 'Provide -Token or create .env from .env.example.'
    }
    $line = Get-Content (Join-Path $repo '.env') | Where-Object { $_ -match '^INTERNAL_API_TOKEN=' } | Select-Object -First 1
    if (-not $line) { throw 'INTERNAL_API_TOKEN is missing from .env.' }
    $Token = $line -replace '^INTERNAL_API_TOKEN=', ''
}

$work = Join-Path $env:LOCALAPPDATA ("Temp/opencode/media-smoke-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Force $work | Out-Null
$png = Join-Path $work 'sample.png'
$text = Join-Path $work 'not-an-image.txt'
$body = Join-Path $work 'body.txt'
$download = Join-Path $work 'download.bin'

Add-Type -AssemblyName System.Drawing
$bitmap = New-Object System.Drawing.Bitmap 8, 8
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.Clear([System.Drawing.Color]::CornflowerBlue)
$graphics.Dispose()
$bitmap.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
Set-Content -Path $text -Value 'not an image' -NoNewline

function Status([string[]]$Arguments) {
    & curl.exe -sS -o $body -w '%{http_code}' @Arguments
}

function StatusWithHeader([string]$Url, [string]$Name, [string]$Value) {
    $request = [System.Net.HttpWebRequest]::Create($Url)
    $request.Headers.Add($Name, $Value)
    try {
        $response = $request.GetResponse()
        try { return [string][int]$response.StatusCode }
        finally { $response.Close() }
    } catch [System.Net.WebException] {
        if ($_.Exception.Response) { return [string][int]$_.Exception.Response.StatusCode }
        throw
    }
}

try {
    $health = & curl.exe -sS "$BaseUrl/actuator/health"
    if ($health -notmatch '"status":"UP"') { throw "Media health is not UP: $health" }

    $unauthorized = Status @('-X', 'POST', "$BaseUrl/api/internal/media",
        '-F', 'ownerId=1', '-F', "file=@$png")
    if ($unauthorized -ne '401') { throw "Unauthorized upload returned $unauthorized, expected 401." }

    $invalid = Status @('-X', 'POST', "$BaseUrl/api/internal/media",
        '-H', "X-Internal-Token: $Token", '-F', 'ownerId=1', '-F', "file=@$text")
    if ($invalid -ne '400') { throw "Invalid image returned $invalid, expected 400." }

    $created = Status @('-X', 'POST', "$BaseUrl/api/internal/media",
        '-H', "X-Internal-Token: $Token", '-F', 'ownerId=1', '-F', "file=@$png")
    if ($created -ne '201') { throw "Upload returned $created, expected 201." }
    $upload = Get-Content $body -Raw | ConvertFrom-Json
    if (-not $upload.url -or -not $upload.path) { throw 'Upload returned no URL/path.' }

    $imageUrl = if ($upload.url -match '^https?://') { $upload.url } else { "$BaseUrl$($upload.url)" }
    $headers = & curl.exe -sS -D - -o $download $imageUrl
    if ($LASTEXITCODE -ne 0) { throw 'Image retrieval failed.' }
    $etagLine = $headers | Where-Object { $_ -match '^ETag:' } | Select-Object -First 1
    if (-not $etagLine) { throw 'Image response has no ETag.' }
    $etag = ($etagLine -replace '^ETag:\s*', '').Trim()
    if (-not (($headers | Where-Object { $_ -match '^Content-Type:\s*image/png' }))) {
        throw 'Image response is not image/png.'
    }
    if (-not (($headers | Where-Object { $_ -match 'immutable' }))) {
        throw 'Image response is missing immutable caching.'
    }
    $sourceHash = (Get-FileHash $png -Algorithm SHA256).Hash
    $downloadHash = (Get-FileHash $download -Algorithm SHA256).Hash
    if ($sourceHash -ne $downloadHash) { throw 'Retrieved bytes differ from the uploaded image.' }

    # curl.exe loses the quotes PowerShell strips from the argument, and the
    # entity-tag must be echoed exactly, so this one goes through .NET.
    $notModified = StatusWithHeader $imageUrl 'If-None-Match' $etag
    if ($notModified -ne '304') { throw "ETag request returned $notModified, expected 304." }

    $stale = StatusWithHeader $imageUrl 'If-None-Match' '"stale-etag"'
    if ($stale -ne '200') { throw "A stale ETag returned $stale, expected 200." }

    Write-Output "MEDIA_SMOKE_OK id=$($upload.id) status=201 retrieval=200 etag=304 stale-etag=200 invalid=400 unauthorized=401"
} finally {
    Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
}

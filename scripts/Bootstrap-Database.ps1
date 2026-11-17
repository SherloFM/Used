param(
    [string]$ContainerDb = 'postgres_db',
    [string]$DbName = 'used',
    [string]$DbUser = 'used',
    [string]$DbPassword = ''
)

# Destructive local bootstrap of the container PostgreSQL database.
# Creates the app role + database, applies the full schema from the host
# Postgres `used` database, then the manual DDL that ddl-auto cannot produce.
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent

if (-not $DbPassword) {
    $envFile = Join-Path $repo '.env'
    if (-not (Test-Path $envFile)) { throw 'Provide -DbPassword or create .env from .env.example.' }
    $line = Get-Content $envFile | Where-Object { $_ -match '^POSTGRES_PASSWORD=' } | Select-Object -First 1
    if (-not $line) { throw 'POSTGRES_PASSWORD is missing from .env.' }
    $DbPassword = $line -replace '^POSTGRES_PASSWORD=', ''
}

$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$pgDump = 'C:\Program Files\PostgreSQL\18\bin\pg_dump.exe'
$tmpDir = Join-Path $env:TEMP 'bootstrap-db'
New-Item -ItemType Directory -Force $tmpDir | Out-Null

function Invoke-ContainerPsqlSql {
    param([string]$Sql, [string]$Database = 'postgres', [string]$User = 'postgres')
    $f = Join-Path $tmpDir 'cmd.sql'
    [System.IO.File]::WriteAllText($f, $Sql, [System.Text.Encoding]::UTF8)
    Get-Content $f -Raw | docker compose exec -T $ContainerDb psql -U $User -d $Database -v ON_ERROR_STOP=1 -f -
    if ($LASTEXITCODE -ne 0) { throw "psql failed (exit $LASTEXITCODE)" }
}

function Invoke-ContainerPsqlFile {
    param([string]$FilePath, [string]$Database = $DbName, [string]$User = 'postgres')
    Get-Content $FilePath -Raw | docker compose exec -T $ContainerDb psql -U $User -d $Database -v ON_ERROR_STOP=1 -f -
    if ($LASTEXITCODE -ne 0) { throw "psql file failed: $FilePath (exit $LASTEXITCODE)" }
}

# NOTE: The container Postgres image creates the POSTGRES_USER (used) as superuser
# and POSTGRES_DB (used) as its database. So role + DB already exist. We just
# apply schema + DDL as that superuser.

$ContainerUser = 'used'

# 1. Dump schema from host Postgres (clean: drop existing objects on apply).
if (-not $env:PGPASSWORD) {
    throw 'Set PGPASSWORD for the local source PostgreSQL connection before running this destructive bootstrap.'
}
$dumpFile = Join-Path $tmpDir 'schema.sql'
# Full dump (schema + data) so the new DB has seed users/listings for end-to-end
# testing. --clean adds DROP statements for idempotent re-runs.
& $pgDump -h localhost -U postgres -d $DbName --no-owner --no-privileges --clean --if-exists -f $dumpFile 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }

# 2. Apply schema as the container superuser.
Invoke-ContainerPsqlFile -FilePath $dumpFile -Database $DbName -User $ContainerUser

# 3. Apply additional DDL (idempotent scripts).
$ddlFiles = @(
    'services/media-service/src/main/resources/db/media-assets.sql',
    'services/notification-service/src/main/resources/db/notification-processed-events.sql',
    'services/notification-service/src/main/resources/db/notification-indexes.sql',
    'services/marketplace-service/src/main/resources/db/payment-indexes.sql',
    'services/chat-service/src/main/resources/db/chat-conversation-visibility.sql'
)
foreach ($rel in $ddlFiles) {
    $full = Join-Path $repo $rel
    if (-not (Test-Path $full)) { throw "Missing DDL file: $rel" }
    Invoke-ContainerPsqlFile -FilePath $full -Database $DbName -User $ContainerUser
    Write-Output "Applied: $rel"
}

# 4. Verify.
Write-Output ''
Write-Output '=== Tables ==='
docker compose exec -T $ContainerDb psql -U $ContainerUser -d $DbName -c '\dt' 2>&1 | Out-String | Write-Output
Write-Output '=== Row counts ==='
$tables = @('users','user_profile','listings','categories','listing_categories','payments','transactions','user_wallets','favorites','notifications','listing_reports','audit_logs','chat_conversations','chat_messages','media_assets','processed_listing_events')
foreach ($t in $tables) {
    $count = (docker compose exec -T $ContainerDb psql -U $ContainerUser -d $DbName -At -c "SELECT count(*) FROM $t;" 2>&1).Trim()
    Write-Output "  $t : $count"
}

# 9. Verify.
Write-Output ''
Write-Output '=== Tables ==='
docker compose exec -T $ContainerDb psql -U $DbUser -d $DbName -c '\dt' 2>&1 | Out-String | Write-Output
Write-Output '=== Row counts ==='
$tables = @('users','user_profile','listings','categories','listing_categories','payments','transactions','user_wallets','favorites','notifications','listing_reports','audit_logs','chat_conversations','chat_messages','media_assets','processed_listing_events')
foreach ($t in $tables) {
    $count = (docker compose exec -T $ContainerDb psql -U $DbUser -d $DbName -At -c "SELECT count(*) FROM $t;" 2>&1).Trim()
    Write-Output "  $t : $count"
}

Write-Output ''
Write-Output 'DATABASE_BOOTSTRAP_OK'
Remove-Item $tmpDir -Recurse -Force -ErrorAction SilentlyContinue

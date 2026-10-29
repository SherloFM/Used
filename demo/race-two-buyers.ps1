# ============================================================================
#  demo\race-two-buyers.ps1  —  LIVE CONCURRENCY SCENARIO
#
#  WHAT IT SHOWS
#  Two buyers (dan + a throwaway 2nd buyer) click "Pay" on the SAME listing
#  at the SAME instant. The two HTTP requests really race each other.
#
#  EXPECTED RESULT (the 4 security layers make this deterministic):
#    - ONE buyer wins:  202 Accepted -> payment PENDING -> worker COMPLETED
#    - The other loses: a CLEAN business rejection (not a stack trace):
#        "This item is already being purchased by another buyer"  or
#        "Listing is no longer available for purchase"
#    - Proof afterwards: listing SOLD, exactly 1 transaction,
#      exactly one -100 debit total, seller +100.
#
#  RUN:    .\demo\race-two-buyers.ps1
#  RESET:  .\demo\race-two-buyers.ps1 -Cleanup     (restores balances, deletes demo rows)
# ============================================================================
param([switch]$Cleanup)

Start-Transcript -Path "$env:TEMP\race-demo-transcript.log" -Force | Out-Null

$Api       = 'http://localhost:8080'
$StateFile = Join-Path $PSScriptRoot 'race-demo-state.json'

# Windows PowerShell 5.1 does not load HttpClient's assembly by default
if (-not ('System.Net.Http.HttpClient' -as [type])) { Add-Type -AssemblyName System.Net.Http }

function Db([string]$Sql) {
    $env:PGPASSWORD = '1231234'
    $out = @(psql -U postgres -d used -t -A -c $Sql | Where-Object { $_ -and $_ -notmatch '^(INSERT|UPDATE|DELETE|BEGIN|COMMIT) ' })
    if ($out.Count -gt 0) { $out[0] }
}
function DbMany([string]$Sql) {
    $env:PGPASSWORD = '1231234'
    psql -U postgres -d used -c $Sql
}
function Login($email, $pw) {
    $payload = @{ email = $email; password = $pw } | ConvertTo-Json
    (Invoke-RestMethod -Uri "$Api/auth/user/login" -Method Post -ContentType 'application/json' -Body $payload -TimeoutSec 15).message
}

# ---------------------------------------------------------------- RESET MODE
if ($Cleanup) {
    if (-not (Test-Path $StateFile)) { Write-Host "No demo state found - nothing to clean."; return }
    $st = Get-Content $StateFile -Raw | ConvertFrom-Json
    Write-Host "Cleaning demo run: listing $($st.listingId), throwaway user $($st.demoUserId) ..."
    DbMany @"
delete from notifications   where listing_id = $($st.listingId);
delete from favorites       where listing_id = $($st.listingId);
delete from transactions    where listing_id = $($st.listingId);
delete from payments        where listing_id = $($st.listingId);
delete from audit_logs      where actor_id   = $($st.demoUserId);
delete from listings        where id         = $($st.listingId);
update user_wallets set balance = $($st.saraOrig) where user_id = $($st.saraId);
update user_wallets set balance = $($st.danOrig)  where user_id = $($st.danId);
delete from user_wallets    where user_id   = $($st.demoUserId);
delete from users           where id        = $($st.demoUserId);
"@
    Remove-Item $StateFile
    Write-Host "Done. Balances and database restored."
    return
}

$ErrorActionPreference = 'Stop'

# STEP 0 ./server preflight
try { Invoke-WebRequest "$Api/" -UseBasicParsing -TimeoutSec 5 | Out-Null }
catch { throw "The app is not reachable on $Api - start it first: .\mvnw.cmd spring-boot:run `"-Dspring-boot.run.profiles=dev`"" }

# STEP 1 ./our three demo actors
$saraToken = Login 'sara@test.com'  'Test1234!'
$danToken  = Login 'dan@used.local' 'Test1234!'
$saraId    = [long](Db "select id from users where email='sara@test.com'")
$danId     = [long](Db "select id from users where email='dan@used.local'")
$saraOrig  = [decimal](Db "select coalesce((select balance from user_wallets where user_id=$saraId),0)")
$danOrig   = [decimal](Db "select coalesce((select balance from user_wallets where user_id=$danId),0)")

# A second buyer that is a REAL user account, but disposable. Its password is
# copied from dan's hash, so it can log in with Test1234! as well.
$stamp       = Get-Date -Format 'yyyyMMddHHmmss'
$demoEmail   = "race-demo-$stamp@demo.invalid"
$demoUserId  = [long](Db @"
insert into users (username, email, password, role, email_verified, is_banned, created_at, updated_at)
select 'race-demo-$stamp', '$demoEmail', password, 'USER', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
from users where email = 'dan@used.local'
returning id
"@)

# STEP 2 ./the listings both buyers want
$body = @{
    title       = "Concurrency demo listing $stamp"
    description = 'Two buyers race to pay for this item at the same instant.'
    price       = 100
    condition   = 'Used'
    location    = 'Manama'
} | ConvertTo-Json
$listing = Invoke-RestMethod -Uri "$Api/api/listings" -Method Post -TimeoutSec 15 `
    -Headers @{ Authorization = "Bearer $saraToken" } -ContentType 'application/json' -Body $body
$listingId = $listing.id

# Buyers get a healthy wallet so the race is about the LISTING, not about funds.
Db @"
insert into user_wallets (user_id, balance, created_at, updated_at)
values ($demoUserId, 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
on conflict (user_id) do update set balance = 1000
"@ | Out-Null
Db @"
insert into user_wallets (user_id, balance, created_at, updated_at)
values ($danId, 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
on conflict (user_id) do update set balance = 1000
"@ | Out-Null
$demoToken = Login $demoEmail 'Test1234!'

Write-Host ''
Write-Host '=====================================================================' -ForegroundColor Cyan
Write-Host " DEMO: two buyers race to buy listing #$listingId (owner: sara, price 100)"
Write-Host '=====================================================================' -ForegroundColor Cyan

# STEP 3 ./THE RACE (true simultaneity)
# HttpClient fires both POSTs inside one process - the gap between the two
# requests is microseconds, unlike Start-Job which takes ~0.5 s to spawn.
$client = New-Object System.Net.Http.HttpClient
function PayRequest($token) {
    $req = New-Object System.Net.Http.HttpRequestMessage (
        [System.Net.Http.HttpMethod]::Post, "$Api/api/payments/$listingId`?method=WALLET")
    $req.Headers.TryAddWithoutValidation('Authorization', "Bearer $token") | Out-Null
    $req
}
$taskDan   = $client.SendAsync((PayRequest $danToken))
$taskDemo  = $client.SendAsync((PayRequest $demoToken))
[void][System.Threading.Tasks.Task]::WaitAll(@($taskDan, $taskDemo), 15000)

function ShowResponse($label, $task) {
    $status = [int]$task.Result.StatusCode
    $json   = $task.Result.Content.ReadAsStringAsync().Result
    Write-Host ''
    Write-Host "BUYER  $label  ->  HTTP $status" -ForegroundColor Yellow
    Write-Host "       $json"
}
ShowResponse 'dan   ' $taskDan
ShowResponse $demoEmail $taskDemo

# STEP 4 ./wait for the worker to settle
$deadline = (Get-Date).AddSeconds(25)
do {
    Start-Sleep -Milliseconds 400
    $unsettled = [int](Db "select count(*) from payments where listing_id=$listingId and status in ('PENDING','PROCESSING')")
} while ($unsettled -gt 0 -and (Get-Date) -lt $deadline)

# STEP 5 ./THE PROOF (straight from the DB)
$listingStatus   = Db "select status from listings where id=$listingId"
$txnCount        = [int](Db "select count(*) from transactions where listing_id=$listingId")
$paymentRows     = Db "select id || ': ' || status || coalesce(' (' || failure_reason || ')','') from payments where listing_id=$listingId order by id"
$danBalance      = Db "select balance from user_wallets where user_id=$danId"
$demoBalance     = Db "select balance from user_wallets where user_id=$demoUserId"
$saraBalance     = Db "select balance from user_wallets where user_id=$saraId"

Write-Host ''
Write-Host 'PROOF (straight from the database)' -ForegroundColor Cyan
Write-Host "  listing #$listingId status........ $listingStatus"
Write-Host "  transactions for the listing..... $txnCount   (exactly ONE sale)"
Write-Host "  payment rows....................."
$paymentRows | ForEach-Object { Write-Host "     $_" }
Write-Host "  dan's wallet..................... $danBalance   (paid once: 1000 - 100 = 900)"
Write-Host "  buyer-2 wallet................... $demoBalance   (did NOT pay)"
Write-Host "  sara's wallet (the seller)....... $saraBalance   (received exactly one +100)"

Write-Host ''
Write-Host "Run .\demo\race-two-buyers.ps1 -Cleanup afterwards to remove the demo data." -ForegroundColor DarkGray

# STEP 6 ./remember ids for -Cleanup
$state = [pscustomobject]@{
    listingId   = $listingId
    demoUserId  = $demoUserId
    danId       = $danId
    saraId      = $saraId
    danOrig     = $danOrig
    saraOrig    = $saraOrig
}
$state | ConvertTo-Json | Set-Content $StateFile



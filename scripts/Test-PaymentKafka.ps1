param([string]$Broker = '')

# Isolate the migrated payment tests while unrelated monolith-era tests still
# prevent module-wide test compilation. Test sources remain in the repository.
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$service = Join-Path $repo 'services/marketplace-service'
$harness = Join-Path $env:LOCALAPPDATA ("Temp/opencode/payment-kafka-tests-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Force "$harness/src/main", "$harness/src/test/java/com/example/marketplace/messaging/payment", "$harness/src/test/java/com/example/marketplace/service" | Out-Null
Copy-Item "$service/pom.xml" "$harness/pom.xml"
Copy-Item "$service/src/main/*" "$harness/src/main" -Recurse
Copy-Item "$service/src/test/java/com/example/marketplace/messaging/payment/*" "$harness/src/test/java/com/example/marketplace/messaging/payment"
Copy-Item "$service/src/test/java/com/example/marketplace/service/PaymentJobRunnerTest.java" "$harness/src/test/java/com/example/marketplace/service"
$previousBroker = $env:KAFKA_TEST_BOOTSTRAP
try {
    $env:KAFKA_TEST_BOOTSTRAP = $Broker
    Write-Host "Test harness: $harness"
    # Windows PowerShell can classify harmless JVM stderr warnings as errors.
    $ErrorActionPreference = 'Continue'
    & "$repo/mvnw.cmd" -B -ntp -f "$harness/pom.xml" test
    $result = $LASTEXITCODE
} finally {
    $env:KAFKA_TEST_BOOTSTRAP = $previousBroker
}
exit $result

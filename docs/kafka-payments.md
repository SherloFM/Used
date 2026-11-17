# Kafka wallet-payment work distribution

## Scope

Kafka distributes wallet-payment jobs after `POST /api/payments/{listingId}`.
Marketplace owns both producer and consumers. COD remains seller-driven.
RabbitMQ continues listing lifecycle delivery and the moderation saga.

```text
Pay -> validate -> commit PENDING payment -> payment-requests (key: listingId)
    -> Marketplace consumer on paymentExecutor -> PaymentJobRunner
    -> PaymentWorker transaction commits -> acknowledge Kafka offset
```

The existing payment worker transaction still claims the payment/listing, moves
wallet balances, writes the transaction, and completes the payment atomically.
The listener invokes it synchronously on a payment-pool thread. There is no nested
executor submission, detached future, or worker timeout.

## Contract and topology

| Item | Value |
|---|---|
| Topic | `payment-requests` |
| Key | Decimal string of `listingId` |
| Group | `marketplace-payment-workers` |
| DLT | `payment-requests.DLT`, same partition as source |
| Partitions | 3 on each topic |
| Replication | 1 (local single broker) |
| Retention | 7 days; DLT also 100 MiB per partition |

```json
{"version":1,"paymentId":108,"listingId":42}
```

Message IDs must be positive, the version must be 1, and the key must match the
listing ID. The runner checks the listing and wallet method against stored data.
Amount, buyer, and seller remain authoritative in the database.

JSON has no producer Java-class headers. The consumer declares its value type.
`ErrorHandlingDeserializer` exposes malformed payloads to the error handler.
`PaymentKafkaValueSerializer` serializes normal records as JSON and preserves raw
`byte[]` values when a malformed payload is sent to the DLT.

## Threads, commits, and failures

The listener container uses `TaskExecutorAdapter(paymentExecutor)`. Its concurrency
must be between 1 and the pool core size, because each consumer loop occupies a
thread. Default: three consumers in the existing four-thread pool. Kafka holds
the backlog, not a second executor task queue.

- `MANUAL_IMMEDIATE`, auto-commit disabled, synchronous offset commits.
- Listener acknowledges only after the runner returns and worker transactions
  have committed. Business rejections such as insufficient funds are recorded
  as FAILED and then acknowledged.
- `IllegalArgumentException`, `IllegalStateException`, and Spring's built-in fatal
  classifications (including deserialization errors) go straight to the DLT.
- Other processing failures get three retries, five seconds apart: four attempts
  total per source record in an uninterrupted retry cycle.
- DLT destination is explicit: this Spring Kafka version defaults to `-dlt`, which
  is not our `.DLT` contract.
- The DLT publisher waits for send success. `ackAfterHandle=false` is paired with
  `commitRecovered=true`; the recovered source offset advances after recovery.
- After confirmed DLT publication, a matching PENDING wallet payment is marked
  FAILED with reason `KAFKA_DLT`. The conditional update never changes COD,
  mismatched listings, or terminal payments. This stops the pending recovery scan
  from endlessly republishing the quarantined payment.
- If DLT publication or that DB update fails, recovery fails and the offset is not
  committed. Retrying recovery can produce duplicate DLT records. Retry counts
  also reset across restarts; the limit is not a persisted lifetime attempt count.
- Malformed messages without usable IDs cannot update a payment; inspect them in
  the DLT. There is no automatic DLT replay. A valid quarantined payment is FAILED;
  resending its old message will not reopen it. Diagnose the cause before initiating
  a new authorized purchase attempt.

Post-commit audit HTTP delivery remains best-effort. Its failure is logged without
retrying the purchase; Kafka does not add audit delivery guarantees.

## Publication recovery without schema changes

The producer sends after the payment save commits, or registers an after-commit
callback if a surrounding transaction exists. A failed send leaves the stored
payment PENDING; the HTTP response means accepted for processing, not broker
confirmation or payment completion.

`PaymentDispatchRecovery` scans stale PENDING wallet payments (including legacy
null method values) every minute, with a two-minute age threshold. It republishes
IDs in batches of 100 with a rotating ID cursor. Failed or completed payments are
excluded. Multiple instances may publish duplicates; the worker's atomic payment
claim and conditional completion prevent repeat money movement.

This uses the existing payments table; it is not an outbox. Ordering is Kafka
append order within a partition, not HTTP arrival order. A recovered missed request
can appear later than another request. Moving an unrecoverable job to the DLT lets
later jobs proceed; later manual replay cannot restore original ordering.

Rebalances and a crash between DB commit and offset commit can still redeliver.
Synchronous listener execution removes orphaned local tasks, not all distributed
duplicate delivery. Database claims remain essential. Long transactions must fit
within `max.poll.interval.ms` (five minutes); tune database timeouts and observe
consumer lag before scaling.

## Docker and configuration

`apache/kafka:4.1.0` runs KRaft broker/controller with a persistent `kafka_data`
volume. Host clients use `localhost:9092`; containers use `kafka:19092`.
Set a stable `KAFKA_CLUSTER_ID` in the ignored `.env`. Generate one with Kafka's
`/opt/kafka/bin/kafka-storage.sh random-uuid`, or a URL-safe Base64 encoding of a
random 16-byte UUID. Do not change it for an existing data volume.

```powershell
docker compose config --quiet
docker compose up -d --wait kafka
docker compose build marketplace-service
docker compose up -d --no-build marketplace-service
docker compose logs --tail 100 marketplace-service kafka
```

Inspect work distribution:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:19092 --describe --group marketplace-payment-workers
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --describe --topic payment-requests
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 --topic payment-requests.DLT --from-beginning --timeout-ms 10000 --property print.key=true
```

KafkaAdmin declares the two production topics when the updated Marketplace starts.
This is a local plaintext, single-broker setup, not broker high availability.
Fixed host port 8083 prevents naive Compose scaling of Marketplace; a worker-only
port override or gateway setup is needed for a multiple-instance deployment.

## Verification

The repository still has monolith-era test compilation failures. The reproducible
isolated runner copies current production sources and the migrated payment tests
to a fresh temporary Maven harness, leaving the old tests intact:

```powershell
# 19 isolated tests; live-broker test skipped.
.\scripts\Test-PaymentKafka.ps1

# Also run the live Kafka test against an already running broker.
.\scripts\Test-PaymentKafka.ps1 -Broker localhost:9092
```

Recorded: 19 isolated tests (including two H2 repository-query tests) and one
live-broker test pass. The repository tests verify recovery selection and that
DLT failure updates cannot alter COD, completed, or mismatched payments.
The live test uses the
production serializer, error handler, listener, and executor configuration with a
mocked payment runner/worker. It creates and deletes uniquely named probe topics.
It verifies three-consumer distribution, transient retries, immediate DLT routing,
raw malformed-byte preservation, recovered offset commits, and no acknowledgement
while the last job is blocked. It does not execute a wallet transfer or prove
end-to-end business integration. Database schema/data initialization and the full
payment concurrency/runtime checks remain separate prerequisites.

The updated Marketplace Docker image also built and started successfully:
`/actuator/health` returned HTTP 200 / UP. Kafka CLI inspection confirmed both
production topics with three partitions and the configured retention, and three
members of `marketplace-payment-workers`, each assigned one partition. Production
topics were empty during this check; no actual payment was submitted.

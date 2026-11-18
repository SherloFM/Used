# Used Marketplace — Microservice Architecture

```mermaid
flowchart TB
    USER([Browser / Mobile User])
    CF[Cloudflare DNS<br/>used.thrifty.bid]
    EDGE[NGINX Gateway<br/>HTTPS :80 / :443]
    FE[Frontend Service<br/>Thymeleaf + JavaScript<br/>:8080]

    USER -->|HTTPS| CF --> EDGE
    EDGE -->|pages and static assets| FE

    subgraph SERVICES[Spring Boot Microservices]
      direction LR
      ACC[Accounts<br/>:8081]
      CHAT[Chat + WebSocket<br/>:8082]

      subgraph MARKETPLACE[Marketplace Service :8083]
        direction TB
        MKTAPI[Listing / Category / Wallet / Payment APIs]
        PAYWORKERS[Payment Worker Pool<br/>atomic local transaction]
      end

      FAV[Favorites<br/>:8084]
      NOTIFY[Notification + SSE<br/>:8085]
      MOD[Moderation<br/>:8086]
      AUDIT[Audit<br/>:8087]
      MEDIA[Media<br/>:8088]
    end

    EDGE -->|REST / auth + profile| ACC
    EDGE -->|REST + WebSocket| CHAT
    EDGE -->|REST| MKTAPI
    EDGE -->|REST| FAV
    EDGE -->|REST + SSE| NOTIFY
    EDGE -->|REST| MOD
    EDGE -->|GET images| MEDIA

    MKTAPI -->|REST: user lookup| ACC
    MKTAPI -->|REST: notify| NOTIFY
    MKTAPI -->|REST: audit| AUDIT
    MKTAPI -->|REST: upload| MEDIA
    ACC -->|REST: email / push| NOTIFY
    ACC -->|REST: audit| AUDIT
    ACC -->|REST: avatar upload| MEDIA
    CHAT -->|REST: users| ACC
    CHAT -->|REST: listings| MKTAPI
    FAV -->|REST: users| ACC
    FAV -->|REST: listing snapshot| MKTAPI
    FAV -->|REST: notify| NOTIFY
    MOD -->|REST: users| ACC
    MOD -->|REST: listing read / immediate delist| MKTAPI
    NOTIFY -->|REST: user email| ACC

    subgraph MESSAGING[Asynchronous Messaging]
      direction LR
      RMQ{{RabbitMQ<br/>listing.commands + listing.events}}
      KAFKA{{Kafka<br/>payment-requests<br/>3 partitions}}
      DLT[(payment-requests.DLT)]
    end

    MKTAPI -->|publish listing created / updated / sold / delisted| RMQ
    MOD -->|publish delist command| RMQ
    RMQ -->|status updates + cleanup| FAV
    RMQ -->|sold / delisted notifications| NOTIFY
    RMQ -->|delist saga result| MOD
    RMQ -->|idempotent delist command| MKTAPI

    MKTAPI -->|key = listingId| KAFKA
    KAFKA -->|consumer group:<br/>marketplace-payment-workers| PAYWORKERS
    KAFKA -. retry exhausted .-> DLT

    PG[(PostgreSQL<br/>one physical schema<br/>service-owned tables)]
    SERVICES -. scalar IDs across service boundaries .-> PG
    PAYWORKERS -->|payment + listing + wallet + transaction<br/>one local DB transaction| PG

    ALMOTAWAJ[Almotawaj Wallet Gateway]
    SMTP[SMTP Provider]
    MKTAPI <-->|HTTPS checkout / HMAC callback| ALMOTAWAJ
    NOTIFY -->|email| SMTP

    classDef edge fill:#e8f1ff,stroke:#2563eb,stroke-width:2px,color:#15315b;
    classDef service fill:#f8fafc,stroke:#475569,stroke-width:1.5px,color:#172033;
    classDef marketplace fill:#eefcf5,stroke:#0f9f6e,stroke-width:2px,color:#114737;
    classDef broker fill:#fff6e5,stroke:#e58a00,stroke-width:2px,color:#613900;
    classDef data fill:#f4edff,stroke:#7c3aed,stroke-width:2px,color:#382064;
    classDef external fill:#fff1f2,stroke:#e11d48,stroke-width:1.5px,color:#67152a;

    class CF,EDGE,FE edge;
    class ACC,CHAT,FAV,NOTIFY,MOD,AUDIT,MEDIA service;
    class MKTAPI,PAYWORKERS marketplace;
    class RMQ,KAFKA broker;
    class PG,DLT data;
    class ALMOTAWAJ,SMTP external;
```

## Communication rules

- **REST** is used for immediate reads and commands. Internal requests carry
  `X-Internal-Token`; browser requests use the `used_auth` HttpOnly JWT cookie
  and `used_csrf` / `X-XSRF-TOKEN` CSRF pair through NGINX.
- **RabbitMQ** propagates listing status changes and runs the moderation delist
  saga with retries and dead-letter queues. It is not used for payment work.
- **Kafka** distributes `payment-requests`, keyed by `listingId`, across the
  `marketplace-payment-workers` consumer group. The worker commits only after
  Marketplace's local payment/listing/wallet/transaction transaction commits.
- Each service owns its tables. References to another service are scalar IDs;
  services do not join across ownership boundaries.

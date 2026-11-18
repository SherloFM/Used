# Used Marketplace ERD

Source: `deploy/ec2/schema.sql` (16 tables). The colored grouping is represented
by the service labels in each entity name.

**Notation**

- Solid relationship: physical PostgreSQL foreign key in the deployed schema.
- Dashed relationship: scalar application reference; no database foreign key.
- `PK`: primary key, `FK`: physical foreign key, `UK`: unique key.

```mermaid
erDiagram
    USERS {
        bigint id PK
        bigint user_profile_id FK,UK
        varchar email
        varchar username
        varchar password
        varchar role
        boolean email_verified
        boolean is_banned
        timestamp created_at
        timestamp updated_at
    }

    USER_PROFILE {
        bigint id PK
        bigint user_profile_id FK,UK
        varchar first_name
        varchar last_name
        varchar phone_number
        varchar profile_bio
        varchar img_url
        bytea img
        varchar imgtype
        timestamp created_at
        timestamp updated_at
    }

    LISTINGS {
        bigint id PK
        bigint user_id FK
        varchar title
        varchar description
        double price
        varchar condition
        varchar location
        varchar status
        varchar status_reason
        varchar img_url
        timestamp created_at
        timestamp updated_at
    }

    CATEGORIES {
        bigint id PK
        varchar name UK
        varchar description
        timestamp created_at
    }

    LISTING_CATEGORIES {
        bigint listing_id PK,FK
        bigint category_id PK,FK
    }

    PAYMENTS {
        bigint id PK
        bigint listing_id FK
        bigint buyer_id FK
        bigint seller_id FK
        numeric amount
        varchar method
        varchar status
        varchar failure_reason
        timestamp expires_at
        timestamp processed_at
        timestamp created_at
        timestamp updated_at
    }

    TRANSACTIONS {
        bigint id PK
        bigint listing_id FK,UK
        bigint buyer_id FK
        bigint seller_id FK
        double amount
        varchar status
        timestamp created_at
        timestamp updated_at
    }

    USER_WALLETS {
        bigint id PK
        bigint user_id FK,UK
        numeric balance
        timestamp created_at
        timestamp updated_at
    }

    FAVORITES {
        bigint id PK
        bigint user_id
        bigint listing_id
        timestamp created_at
    }

    CHAT_CONVERSATIONS {
        bigint id PK
        bigint listing_id FK
        bigint buyer_id FK
        bigint seller_id FK
        boolean hidden_for_buyer
        boolean hidden_for_seller
        timestamp created_at
        timestamp last_message_at
    }

    CHAT_MESSAGES {
        bigint id PK
        bigint conversation_id FK
        bigint sender_id FK
        text content
        boolean read
        timestamp sent_at
    }

    LISTING_REPORTS {
        bigint id PK
        bigint listing_id FK
        bigint reporter_id FK
        bigint admin_id FK
        varchar reason
        varchar status
        varchar description
        varchar admin_notes
        timestamp created_at
        timestamp updated_at
        timestamp resolved_at
    }

    NOTIFICATIONS {
        bigint id PK
        bigint user_id
        bigint payment_id
        bigint listing_id
        varchar type
        varchar title
        varchar message
        varchar img_url
        boolean is_read
        timestamp created_at
    }

    PROCESSED_LISTING_EVENTS {
        varchar event_id PK
        bigint listing_id
        varchar event_type
        timestamp processed_at
    }

    AUDIT_LOGS {
        bigint id PK
        bigint actor_id
        varchar actor_username
        varchar action
        text details
        timestamp created_at
    }

    MEDIA_ASSETS {
        uuid id PK
        bigint owner_id
        varchar content_type
        bigint size_bytes
        char sha256
        bytea content
        timestamp created_at
    }

    %% Physical foreign keys from deploy/ec2/schema.sql
    USER_PROFILE o|--o| USER_PROFILE : legacy_self_FK
    USER_PROFILE o|--o| USERS : profile
    USERS ||--o{ LISTINGS : owns
    LISTINGS ||--o{ LISTING_CATEGORIES : classified_by
    CATEGORIES ||--o{ LISTING_CATEGORIES : contains
    LISTINGS ||--o{ PAYMENTS : payment_attempts
    USERS ||--o{ PAYMENTS : buyer
    USERS ||--o{ PAYMENTS : seller
    LISTINGS ||--o| TRANSACTIONS : completed_sale
    USERS ||--o{ TRANSACTIONS : buyer
    USERS ||--o{ TRANSACTIONS : seller
    USERS ||--o| USER_WALLETS : wallet
    LISTINGS ||--o{ CHAT_CONVERSATIONS : conversations
    USERS ||--o{ CHAT_CONVERSATIONS : buyer
    USERS ||--o{ CHAT_CONVERSATIONS : seller
    CHAT_CONVERSATIONS ||--o{ CHAT_MESSAGES : messages
    USERS ||--o{ CHAT_MESSAGES : sender
    LISTINGS ||--o{ LISTING_REPORTS : reports
    USERS ||--o{ LISTING_REPORTS : reporter
    USERS o|--o{ LISTING_REPORTS : resolving_admin

    %% Logical scalar references without physical FKs
    USERS ||..o{ FAVORITES : user_id
    LISTINGS ||..o{ FAVORITES : listing_id
    USERS ||..o{ NOTIFICATIONS : user_id
    PAYMENTS o|..o{ NOTIFICATIONS : payment_id
    LISTINGS o|..o{ NOTIFICATIONS : listing_id
    LISTINGS o|..o{ PROCESSED_LISTING_EVENTS : listing_id
    USERS o|..o{ AUDIT_LOGS : actor_id
    USERS ||..o{ MEDIA_ASSETS : owner_id
    MEDIA_ASSETS o|..o{ LISTINGS : img_url_path
    MEDIA_ASSETS o|..o{ USER_PROFILE : img_url_path
```

## Service ownership

| Service | Owned tables |
|---|---|
| Accounts | `users`, `user_profile` |
| Marketplace | `listings`, `categories`, `listing_categories`, `payments`, `transactions`, `user_wallets` |
| Favorites | `favorites` |
| Chat | `chat_conversations`, `chat_messages` |
| Moderation | `listing_reports` |
| Notification | `notifications`, `processed_listing_events` |
| Audit | `audit_logs` |
| Media | `media_assets` |

The shared physical schema still contains legacy cross-service foreign keys. New
service code treats cross-service links as scalar IDs and resolves them over the
service contracts, as required by `docs/architecture-decisions.md`.

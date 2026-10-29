# 🛒 Used Marketplace

A full-stack second-hand goods trading platform built with **Spring Boot 4.1.1**, **Thymeleaf**, and **PostgreSQL**. Users can browse, save, report, buy and sell listings, with a separate administrator portal.

![Tech Stack](https://img.shields.io/badge/Tech%20Stack-Java%20|%20Spring%20Boot%20|%20PostgreSQL-blue) ![License](https://img.shields.io/badge/License-MIT-green)

## ✨ Key Features

*   **Secure Authentication**: JWT-based stateless security with email verification workflows and rate-limited password resets.
*   **Advanced Search Engine**: Real-time keyword searching combined with multi-category tagging (filters) and dynamic pagination.
*   **Listing Management**: Users can create rich media listings with image uploads, condition grading, and geographic tagging.
*   **Real-Time Notifications**: Persisted notifications with after-commit SSE delivery, read status, replay, COD alerts and saved-listing updates.
*   **Favorites**: Star toggle and paginated saved-listing grid; sale/delist cleanup with notifications and best-effort email.
*   **Moderation**: Listing reports, review/resolve workflow, user management and role-based login routing.
*   **Payments**: Asynchronous wallet processing and seller-managed cash-on-delivery requests.
*   **Comprehensive Audit Trail**: Every user action (Login, Create, Buy, Edit) is logged immutably and displayed in a personalized activity timeline.
*   **Responsive UI**: Built with semantic HTML5/CSS3 and vanilla JavaScript for maximum performance without heavy framework overhead.

## 🛠️ Technology Stack

### Backend
*   **Language**: Java 17+
*   **Framework**: Spring Boot 4.1.1
    *   Spring Web MVC
    *   Spring Data JPA (Hibernate)
    *   Spring Security (JWT Implementation)
    *   Spring Mail (SMTP Integration)
*   **Database**: PostgreSQL
*   **Build Tool**: Maven

### Frontend
*   **Templating**: Thymeleaf
*   **Styling**: Custom CSS3 (Flexbox/Grid), Font Awesome Icons
*   **Logic**: Vanilla ES6+ JavaScript (Fetch API, LocalStorage, EventSource)

## 🚀 Getting Started

### Prerequisites
*   JDK 17 or higher installed.
*   Maven installed.
*   PostgreSQL running locally or remotely.

### Installation Steps

1.  **Clone the Repository**
    ```bash
     # Clone your actual repository URL, then enter the Used checkout.
     cd Used
    ```

2.  **Configure Database**
    Update `src/main/resources/application-dev.properties` with your PostgreSQL credentials:
    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/used_marketplace_db
    spring.datasource.username=your_username
    spring.datasource.password=your_password
    
    # SMTP Config for Emails
    spring.mail.host=smtp.gmail.com
    spring.mail.port=587
    spring.mail.username=your-email@gmail.com
    spring.mail.password=your-app-password
    ```

3.  **Run the Application**
    Using Maven wrapper:
    ```bash
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
    ```
    Or directly from IDE (IntelliJ/Eclipse): Run `UsedApplication.java` ensuring the `dev` profile is active.

4.  **Access the App**
    Navigate to `http://localhost:8080` in your browser.

## 📂 Project Structure

```text
src/main/java/com/example/Used
├── Controller      # REST Endpoints & View Mappings
├── Model           # Entities (User, Listing, Transaction, AuditLog)
├── Repository      # JPA Interfaces for DB Access
├── Service         # Business Logic Layer
├── Security        # JWT Filters, Config, UserDetails
└── Exceptions      # Global Error Handling
```

## Development and verification

Windows PowerShell (from the project root):
```powershell
.\mvnw.cmd compile
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
.\mvnw.cmd test "-Dtest=PaymentConcurrencyDbTest" "-Dspring.jpa.show-sql=false"
```

`dev` uses PostgreSQL configured in `application-dev.properties`. Override datasource
and mail settings for your environment; do not copy credentials into documentation.
Hibernate `ddl-auto=update` manages entity tables. Inspect the SQL files in
`src/main/resources/db/` for additional indexes; entity schema updates do not imply
those scripts have been applied. The active-payment unique index is significant for concurrency.

The concurrency suite uses real PostgreSQL, unique committed fixtures, the real
payment executor and a recording mail sender (no external mail delivery). It checks
competing submissions, wallet overspending, rollback, the legacy purchase path and
favorite/sale overlap. Fixtures are deleted by their IDs after tasks finish; there
is no test-wide rollback. A failed race test may describe a production gap rather
than a broken test. Other existing dev-profile tests may use seed data and real mail.

## UI conventions

All full-page templates load `static/css/theme.css` after their page styles. It owns
the shared purple/blue primary action palette, semantic success/error colors, card
shape, typography, keyboard focus, mobile layout and reduced-motion rules. Favorites
use a star consistently. Admin navigation retains its dark background and active-page
indicator; logout and marketplace links remain available on mobile.

## Documentation

- [API reference](API%20docs.MD): endpoint auth, request/response shapes and side effects.
- [User stories](UserStories.MD): features and acceptance criteria.
- [Review findings](REVIEW.md): verification results and remaining consistency gaps.

The maintained README is `README.MD` (there is no separate `Readme.docs` file).

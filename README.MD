# 🛒 Used Marketplace

A modern, full-stack second-hand goods trading platform built with **Spring Boot 3**, **Thymeleaf**, and **PostgreSQL**. This application enables users to safely buy and sell used electronics, furniture, vehicles, and more through a secure, verified community environment.

![Tech Stack](https://img.shields.io/badge/Tech%20Stack-Java%20|%20Spring%20Boot%20|%20PostgreSQL-blue) ![License](https://img.shields.io/badge/License-MIT-green)

## ✨ Key Features

*   **Secure Authentication**: JWT-based stateless security with email verification workflows and rate-limited password resets.
*   **Advanced Search Engine**: Real-time keyword searching combined with multi-category tagging (filters) and dynamic pagination.
*   **Listing Management**: Users can create rich media listings with image uploads, condition grading, and geographic tagging.
*   **Real-Time Notifications**: Integrated Server-Sent Events (SSE) engine pushes instant alerts to sellers when their items are viewed or interacted with.
*   **Comprehensive Audit Trail**: Every user action (Login, Create, Buy, Edit) is logged immutably and displayed in a personalized activity timeline.
*   **Responsive UI**: Built with semantic HTML5/CSS3 and vanilla JavaScript for maximum performance without heavy framework overhead.

## 🛠️ Technology Stack

### Backend
*   **Language**: Java 17+
*   **Framework**: Spring Boot 3.x
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
    git clone https://github.com/your-username/used-marketplace.git
    cd used-marketplace
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
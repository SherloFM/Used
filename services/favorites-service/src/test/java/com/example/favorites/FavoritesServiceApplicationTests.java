package com.example.favorites;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:favorites-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "internal.api-token=test-internal-token-at-least-32-characters",
        "security.jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcw==",
        "security.jwt.issuer=used-accounts",
        "security.jwt.audience=used-api",
        "services.accounts.base-url=http://localhost:8081",
        "services.marketplace.base-url=http://localhost:8083",
        "services.notification.base-url=http://localhost:8085",
        "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"
})
class FavoritesServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

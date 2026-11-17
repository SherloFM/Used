package com.example.notification.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@CucumberContextConfiguration
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-cucumber;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "internal.api-token=test-internal-token-at-least-32-characters",
        "security.jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcw==",
        "security.jwt.issuer=used-accounts",
        "security.jwt.audience=used-api",
        "services.accounts.base-url=http://localhost:9999",
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"
})
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {
}

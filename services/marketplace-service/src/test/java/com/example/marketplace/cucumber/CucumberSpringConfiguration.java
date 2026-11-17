package com.example.marketplace.cucumber;

import com.example.marketplace.client.AccountsClient;
import com.example.marketplace.client.AuditClient;
import com.example.marketplace.client.MediaClient;
import com.example.marketplace.client.NotificationClient;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@CucumberContextConfiguration
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:marketplace-cucumber;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "internal.api-token=test-internal-token-at-least-32-characters",
        "security.jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcw==",
        "security.jwt.issuer=used-accounts",
        "security.jwt.audience=used-api",
        "services.accounts.base-url=http://localhost:9999",
        "services.audit.base-url=http://localhost:9999",
        "services.notification.base-url=http://localhost:9999",
        "services.media.base-url=http://localhost:9999",
        "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration,org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
})
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {
    @MockitoBean
    public RabbitTemplate rabbitTemplate;

    @MockitoBean
    public KafkaTemplate<Object, Object> kafkaTemplate;

    @MockitoBean
    public ConsumerFactory<Object, Object> consumerFactory;

    @MockitoBean
    public AccountsClient accountsClient;

    @MockitoBean
    public AuditClient auditClient;

    @MockitoBean
    public MediaClient mediaClient;

    @MockitoBean
    public NotificationClient notificationClient;
}

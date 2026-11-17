package com.example.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:marketplace-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "internal.api-token=test-internal-token-at-least-32-characters",
        "security.jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcw==",
        "security.jwt.issuer=used-accounts",
        "security.jwt.audience=used-api",
        "services.accounts.base-url=http://localhost:8081",
        "services.audit.base-url=http://localhost:8087",
        "services.notification.base-url=http://localhost:8085",
        "services.media.base-url=http://localhost:8088",
        "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration,org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
})
class MarketplaceServiceApplicationTests {
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @MockitoBean
    private ConsumerFactory<Object, Object> consumerFactory;

    @Test
    void contextLoads() {
    }
}

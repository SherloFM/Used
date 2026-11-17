package com.example.marketplace.messaging.payment;

import com.example.marketplace.model.Listings;
import com.example.marketplace.model.Payment;
import com.example.marketplace.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentDispatchRepositoryTest {
    private LocalContainerEntityManagerFactoryBean factory;
    private EntityManager em;
    private PaymentRepository payments;
    private TransactionTemplate transaction;

    @BeforeEach
    void setup() {
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        factory.setPackagesToScan("com.example.marketplace.model");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        var emf = factory.getObject();
        em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        payments = new JpaRepositoryFactory(em).getRepository(PaymentRepository.class);
        transaction = new TransactionTemplate(new JpaTransactionManager(emf));
    }

    @AfterEach
    void close() {
        if (factory != null) factory.destroy();
    }

    @Test
    void dltFailureIsConditionalAndExcludesTerminalCodAndWrongListings() {
        transaction.executeWithoutResult(status -> {
            Payment wallet = persist(Payment.Method.WALLET, Payment.Status.PENDING);
            Payment completed = persist(Payment.Method.WALLET, Payment.Status.COMPLETED);
            Payment cod = persist(Payment.Method.COD, Payment.Status.PENDING);
            assertEquals(0, fail(wallet, wallet.getListing().getId() + 1000));
            assertEquals(0, fail(completed, completed.getListing().getId()));
            assertEquals(0, fail(cod, cod.getListing().getId()));
            assertEquals(1, fail(wallet, wallet.getListing().getId()));
            assertEquals(0, fail(wallet, wallet.getListing().getId()));
            Payment failed = payments.findById(wallet.getId()).orElseThrow();
            assertEquals(Payment.Status.FAILED, failed.getStatus());
            assertEquals("KAFKA_DLT", failed.getFailureReason());
            assertNotNull(failed.getProcessedAt());
        });
    }

    @Test
    void recoveryQuerySelectsWalletAndLegacyPendingRowsOnly() {
        transaction.executeWithoutResult(status -> {
            Payment wallet = persist(Payment.Method.WALLET, Payment.Status.PENDING);
            Payment legacy = persist(null, Payment.Status.PENDING);
            persist(Payment.Method.COD, Payment.Status.PENDING);
            persist(Payment.Method.WALLET, Payment.Status.FAILED);
            persist(Payment.Method.WALLET, Payment.Status.COMPLETED);
            var batch = payments.findPendingDispatchBatch(Payment.Status.PENDING, Payment.Method.WALLET,
                    LocalDateTime.now().plusMinutes(1), 0L, PageRequest.of(0, 100));
            assertEquals(2, batch.size());
            assertEquals(wallet.getId(), batch.get(0)[0]);
            assertEquals(legacy.getId(), batch.get(1)[0]);
            assertTrue(payments.findPendingDispatchBatch(Payment.Status.PENDING, Payment.Method.WALLET,
                    LocalDateTime.now().minusMinutes(1), 0L, PageRequest.of(0, 100)).isEmpty());
        });
    }

    private int fail(Payment payment, Long listingId) {
        return payments.failPendingDispatch(payment.getId(), listingId,
                Payment.Status.PENDING, Payment.Status.FAILED, Payment.Method.WALLET);
    }

    private Payment persist(Payment.Method method, Payment.Status status) {
        Listings listing = new Listings();
        listing.setUserId(2L);
        listing.setTitle("Bike");
        listing.setDescription("Test listing");
        listing.setCondition("Good");
        listing.setLocation("Test city");
        listing.setPrice(10);
        listing.setStatus(Listings.Status.ACTIVE);
        em.persist(listing);
        Payment payment = new Payment();
        payment.setListing(listing);
        payment.setBuyerId(1L);
        payment.setSellerId(2L);
        payment.setAmount(BigDecimal.TEN);
        payment.setMethod(method);
        payment.setStatus(status);
        em.persist(payment);
        em.flush();
        return payment;
    }
}

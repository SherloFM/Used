package com.example.marketplace.concurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real PostgreSQL checks for the database primitives that protect payment work.
 * Named *IT so the normal isolated suite does not require Docker; Jenkins runs
 * it in a dedicated stage.
 */
@Testcontainers
class PostgresConcurrencyIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection(); var sql = connection.createStatement()) {
            sql.execute("DROP TABLE IF EXISTS ci_payments, ci_wallets, ci_listings CASCADE");
            sql.execute("CREATE TABLE ci_listings (id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL)");
            sql.execute("CREATE TABLE ci_wallets (user_id BIGINT PRIMARY KEY, balance NUMERIC(12,2) NOT NULL)");
            sql.execute("CREATE TABLE ci_payments (id BIGSERIAL PRIMARY KEY, listing_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL)");
            sql.execute("CREATE UNIQUE INDEX ci_one_active_payment ON ci_payments(listing_id) WHERE status IN ('PENDING','PROCESSING')");
        }
    }

    @Test
    void onlyOneBuyerCanClaimAnActiveListing() throws Exception {
        execute("INSERT INTO ci_listings (id, status) VALUES (7, 'ACTIVE')");

        List<Integer> results = race(() -> update(
                "UPDATE ci_listings SET status='SOLD' WHERE id=7 AND status='ACTIVE'"));

        assertEquals(1, results.stream().mapToInt(Integer::intValue).sum());
        assertEquals("SOLD", scalar("SELECT status FROM ci_listings WHERE id=7", String.class));
    }

    @Test
    void conditionalDebitCannotOverdrawOneWallet() throws Exception {
        execute("INSERT INTO ci_wallets (user_id, balance) VALUES (12, 100.00)");

        List<Integer> results = race(() -> update(
                "UPDATE ci_wallets SET balance=balance-80.00 WHERE user_id=12 AND balance>=80.00"));

        assertEquals(1, results.stream().mapToInt(Integer::intValue).sum());
        assertEquals(new BigDecimal("20.00"), scalar(
                "SELECT balance FROM ci_wallets WHERE user_id=12", BigDecimal.class));
    }

    @Test
    void partialUniqueIndexAllowsOnlyOneActivePayment() throws Exception {
        List<Integer> results = race(() -> {
            try {
                return update("INSERT INTO ci_payments (listing_id, status) VALUES (7, 'PENDING')");
            } catch (SQLException conflict) {
                if ("23505".equals(conflict.getSQLState())) return 0;
                throw conflict;
            }
        });

        assertEquals(1, results.stream().mapToInt(Integer::intValue).sum());
        assertEquals(1L, scalar("SELECT COUNT(*) FROM ci_payments", Long.class));
    }

    private List<Integer> race(Callable<Integer> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> contender = () -> {
                ready.countDown();
                start.await();
                return action.call();
            };
            var first = executor.submit(contender);
            var second = executor.submit(contender);
            ready.await();
            start.countDown();
            return List.of(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private int update(String statement) throws SQLException {
        try (Connection connection = connection(); var sql = connection.createStatement()) {
            return sql.executeUpdate(statement);
        }
    }

    private void execute(String statement) throws SQLException {
        update(statement);
    }

    private <T> T scalar(String query, Class<T> type) throws SQLException {
        try (Connection connection = connection(); var sql = connection.createStatement(); var rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getObject(1, type);
        }
    }
}

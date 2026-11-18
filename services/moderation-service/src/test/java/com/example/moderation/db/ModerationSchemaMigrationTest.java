package com.example.moderation.db;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationSchemaMigrationTest {

    @Test
    void delistingMigrationReplacesTheOldConstraintAndAllowsSagaState() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:moderation-schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE listing_reports (
                            id BIGINT PRIMARY KEY,
                            status VARCHAR(30) NOT NULL,
                            CONSTRAINT listing_reports_status_check CHECK (
                                status IN ('PENDING', 'UNDER_REVIEW', 'DISMISSED', 'ACTION_TAKEN')
                            )
                        )
                        """);
            }

            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/allow-delisting-status.sql"));

            try (var statement = connection.createStatement()) {
                assertEquals(1, statement.executeUpdate(
                        "INSERT INTO listing_reports (id, status) VALUES (1, 'DELISTING')"));
            }
        }
    }
}

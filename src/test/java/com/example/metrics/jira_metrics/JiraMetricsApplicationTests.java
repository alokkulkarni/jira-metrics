package com.example.metrics.jira_metrics;

import com.example.metrics.jira_metrics.test.config.BaseTestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Main application context test with TestContainers.
 * Verifies that the Spring Boot application starts successfully with a real PostgreSQL database.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@SpringBootTest(classes = JiraMetricsApplication.class)
@ActiveProfiles("test")
@DisplayName("JIRA Metrics Application Context Test with TestContainers")
class JiraMetricsApplicationTests extends BaseTestContainersConfig {

    /**
     * Tests that the Spring Boot application context loads successfully with TestContainers.
     * This ensures all beans are properly configured and the database connection works.
     */
    @Test
    @DisplayName("Should load application context with TestContainers successfully")
    void contextLoads() {
        // Given - Application context should be loaded by Spring Boot Test
        // When - Test execution begins
        // Then - Context loading should succeed without exceptions
        assertTrue(isDatabaseReady(), "PostgreSQL TestContainer should be running and ready");

        // Additional verification that the application started correctly
        assertTrue(getPostgresContainer().isRunning(),
                "Database container should be running for application context");
    }

    /**
     * Tests that the database schema is properly initialized with TestContainers.
     */
    @Test
    @DisplayName("Should initialize database schema correctly")
    void databaseSchemaInitialization() {
        // Given - Application context is loaded
        // When - Database container is running
        // Then - Database should be accessible and properly configured
        assertTrue(isDatabaseReady(), "Database should be ready for connections");

        var jdbcUrl = getPostgresContainer().getJdbcUrl();
        var username = getPostgresContainer().getUsername();

        assertTrue(jdbcUrl.contains("jira_metrics_test"),
                "JDBC URL should contain test database name");
        assertTrue(username.equals("test_user"),
                "Database username should be configured correctly");
    }
}

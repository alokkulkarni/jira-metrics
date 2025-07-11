package com.example.metrics.jira_metrics.test.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base test configuration class providing shared TestContainers setup.
 * This class ensures consistent database container configuration across all integration tests.
 *
 * <p>Usage: Extend this class in integration test classes to automatically get
 * a PostgreSQL TestContainer configured with proper database settings.</p>
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Testcontainers
public abstract class BaseTestContainersConfig {

    private static final Logger logger = LoggerFactory.getLogger(BaseTestContainersConfig.class);

    private static final String POSTGRES_IMAGE = "postgres:15-alpine";
    private static final String TEST_DATABASE_NAME = "jira_metrics_test";
    private static final String TEST_USERNAME = "test_user";
    private static final String TEST_PASSWORD = "test_password";

    /**
     * Shared PostgreSQL container instance for all tests.
     * Using a static container ensures it's shared across test methods and classes.
     */
    @Container
    protected static final PostgreSQLContainer<?> postgresContainer =
        new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName(TEST_DATABASE_NAME)
            .withUsername(TEST_USERNAME)
            .withPassword(TEST_PASSWORD)
            .withInitScript("schema-test.sql")
            .withReuse(true);

    /**
     * Configures Spring Boot application properties dynamically based on the TestContainer.
     * This method is called automatically by Spring Test framework.
     *
     * @param registry the dynamic property registry to configure
     */
    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        logger.info("Configuring test properties for PostgreSQL container");

        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Spring Data JDBC configuration for tests
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.sql.init.continue-on-error", () -> "false");

        // JPA Configuration for tests
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.show-sql", () -> "true");

        logger.info("Test database configured: URL={}, Username={}",
                   postgresContainer.getJdbcUrl(), postgresContainer.getUsername());
    }

    /**
     * Provides access to the PostgreSQL container for test-specific operations.
     *
     * @return the PostgreSQL container instance
     */
    protected static PostgreSQLContainer<?> getPostgresContainer() {
        return postgresContainer;
    }

    /**
     * Checks if the database container is running and ready for tests.
     *
     * @return true if container is running, false otherwise
     */
    protected boolean isDatabaseReady() {
        return postgresContainer.isRunning() && postgresContainer.isCreated();
    }
}

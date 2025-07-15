package com.example.metrics.jira_metrics.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;

/**
 * Flyway configuration that provides better control over migration execution.
 * Ensures checksum validation and repairs are handled gracefully.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Configuration
public class FlywayConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfiguration.class);

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String[] locations;

    @Value("${spring.flyway.table:flyway_schema_history}")
    private String table;

    @Value("${spring.flyway.baseline-version:0}")
    private String baselineVersion;

    @Value("${spring.flyway.baseline-description:Initial baseline}")
    private String baselineDescription;

    /**
     * Creates a custom Flyway instance with enhanced error handling.
     *
     * @param dataSource the database connection
     * @return configured Flyway instance
     */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        logger.info("Configuring Flyway with enhanced error handling");

        FluentConfiguration configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(locations)
            .table(table)
            .baselineVersion(baselineVersion)
            .baselineDescription(baselineDescription)
            .baselineOnMigrate(true)
            .validateOnMigrate(false)  // We'll handle validation manually
            .outOfOrder(false)
            .createSchemas(true)
            .connectRetries(3)
            .lockRetryCount(5);

        Flyway flyway = configuration.load();

        logger.debug("Flyway configured successfully with locations: {}", (Object) locations);
        return flyway;
    }

    /**
     * Custom Flyway migration initializer that skips automatic migration.
     * Our FlywayMigrationValidator will handle migrations instead.
     *
     * @param flyway the Flyway instance
     * @return migration initializer that does nothing
     */
    @Bean
    @DependsOn("flyway")
    public FlywayMigrationInitializer flywayInitializer(Flyway flyway) {
        logger.info("Creating custom Flyway initializer (migrations handled by FlywayMigrationValidator)");

        return new FlywayMigrationInitializer(flyway, null) {
            @Override
            public void afterPropertiesSet() {
                // Do nothing - let our FlywayMigrationValidator handle migrations
                logger.debug("Flyway initializer called - delegating to FlywayMigrationValidator");
            }
        };
    }
}

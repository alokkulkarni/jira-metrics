package com.example.metrics.jira_metrics.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Flyway migration monitoring and validation service.
 * Ensures migrations run only when needed and validates schema consistency.
 * Handles graceful execution without failures when schema is up-to-date.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Component
public class FlywayMigrationValidator {

    private static final Logger logger = LoggerFactory.getLogger(FlywayMigrationValidator.class);

    private final Flyway flyway;
    private final DataSource dataSource;

    // Expected core tables from our migrations
    private static final Set<String> EXPECTED_TABLES = Set.of(
        "boards", "teams", "jira_data", "sprints", "issues", "board_metrics", "flyway_schema_history"
    );

    /**
     * Constructor for FlywayMigrationValidator.
     *
     * @param flyway Flyway instance
     * @param dataSource Database connection
     */
    public FlywayMigrationValidator(@Autowired Flyway flyway, @Autowired DataSource dataSource) {
        this.flyway = flyway;
        this.dataSource = dataSource;
    }

    /**
     * Validates and ensures migrations are applied after application startup.
     * This runs after the application context is fully initialized.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateAndMigrateOnStartup() {
        logger.info("=== Starting Flyway Migration Validation ===");

        try {
            // Step 1: Check if database schema exists
            boolean schemaExists = checkSchemaExists();
            logger.info("Database schema exists: {}", schemaExists);

            // Step 2: Handle checksum validation issues first
            handleChecksumValidation();

            // Step 3: Analyze migration state
            MigrationAnalysis analysis = analyzeMigrationState();
            logMigrationAnalysis(analysis);

            // Step 4: Execute migrations if needed
            if (analysis.needsMigration()) {
                executeRequiredMigrations(analysis);
            } else {
                logger.info("✅ Database schema is up-to-date. No migrations needed.");
            }

            // Step 5: Final validation
            validateSchemaIntegrity();

            logger.info("=== Flyway Migration Validation Completed Successfully ===");

        } catch (Exception e) {
            logger.error("❌ Flyway migration validation failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Database migration validation failed", e);
        }
    }

    /**
     * Handles checksum validation issues by attempting repair when needed.
     */
    private void handleChecksumValidation() {
        try {
            logger.debug("🔍 Performing checksum validation...");

            // Attempt to get migration info to check for validation issues
            MigrationInfo[] migrations = flyway.info().all();

            boolean hasChecksumMismatch = false;
            for (MigrationInfo migration : migrations) {
                if (migration.getState() == MigrationState.SUCCESS) {
                    // Check if this is a checksum mismatch by attempting validation
                    try {
                        // This will throw an exception if there's a checksum mismatch
                        flyway.validate();
                        break; // If we get here, validation passed
                    } catch (FlywayException e) {
                        if (e.getMessage().contains("checksum mismatch") ||
                            e.getMessage().contains("Validate failed")) {
                            hasChecksumMismatch = true;
                            logger.warn("⚠️  Detected checksum mismatch in migrations: {}", e.getMessage());
                            break;
                        }
                        throw e; // Re-throw if it's not a checksum issue
                    }
                }
            }

            if (hasChecksumMismatch) {
                logger.info("🔧 Attempting to repair checksum mismatches...");
                flyway.repair();
                logger.info("✅ Checksum repair completed successfully");

                // Validate again after repair
                flyway.validate();
                logger.info("✅ Post-repair validation passed");
            } else {
                logger.debug("✅ No checksum issues detected");
            }

        } catch (FlywayException e) {
            if (e.getMessage().contains("checksum mismatch") ||
                e.getMessage().contains("Validate failed")) {

                logger.warn("⚠️  Checksum validation failed, attempting repair: {}", e.getMessage());
                try {
                    flyway.repair();
                    logger.info("✅ Flyway repair completed successfully");

                    // Validate again after repair
                    flyway.validate();
                    logger.info("✅ Post-repair validation passed");

                } catch (FlywayException repairException) {
                    logger.error("❌ Repair failed: {}", repairException.getMessage());
                    throw new IllegalStateException("Failed to repair Flyway checksum issues", repairException);
                }
            } else {
                throw e; // Re-throw if it's not a checksum issue
            }
        }
    }

    /**
     * Checks if the database schema exists and has expected tables.
     *
     * @return true if schema exists with tables
     */
    private boolean checkSchemaExists() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            Set<String> existingTables = new HashSet<>();

            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME").toLowerCase();
                    existingTables.add(tableName);
                }
            }

            logger.debug("Existing tables: {}", existingTables);

            // Check if we have at least some core tables
            long matchingTables = EXPECTED_TABLES.stream()
                .filter(existingTables::contains)
                .count();

            return matchingTables > 0;

        } catch (SQLException e) {
            logger.warn("Could not check schema existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Analyzes the current migration state and determines what actions are needed.
     *
     * @return Migration analysis result
     */
    private MigrationAnalysis analyzeMigrationState() {
        try {
            MigrationInfo[] migrations = flyway.info().all();

            int pendingCount = 0;
            int failedCount = 0;
            int successCount = 0;
            boolean hasBaseline = false;

            for (MigrationInfo migration : migrations) {
                MigrationState state = migration.getState();

                switch (state) {
                    case PENDING -> pendingCount++;
                    case SUCCESS -> successCount++;
                    case FAILED -> failedCount++;
                    case BASELINE -> hasBaseline = true;
                }
            }

            return new MigrationAnalysis(
                migrations.length,
                successCount,
                pendingCount,
                failedCount,
                hasBaseline
            );

        } catch (FlywayException e) {
            logger.warn("Could not analyze migration state, will attempt baseline: {}", e.getMessage());
            // If we can't get migration info, we likely need to baseline
            return new MigrationAnalysis(0, 0, 0, 0, false);
        }
    }

    /**
     * Logs the migration analysis results.
     *
     * @param analysis Migration analysis result
     */
    private void logMigrationAnalysis(MigrationAnalysis analysis) {
        logger.info("Migration Analysis:");
        logger.info("  📋 Total migrations: {}", analysis.totalMigrations());
        logger.info("  ✅ Successful migrations: {}", analysis.successfulMigrations());
        logger.info("  ⏳ Pending migrations: {}", analysis.pendingMigrations());
        logger.info("  ❌ Failed migrations: {}", analysis.failedMigrations());
        logger.info("  🏁 Has baseline: {}", analysis.hasBaseline());
        logger.info("  🔄 Needs migration: {}", analysis.needsMigration());
    }

    /**
     * Executes required migrations based on analysis.
     *
     * @param analysis Migration analysis result
     */
    private void executeRequiredMigrations(MigrationAnalysis analysis) {
        try {
            logger.info("🚀 Executing required database migrations...");

            if (!analysis.hasBaseline() && analysis.totalMigrations() == 0) {
                // No migration history at all - likely first run
                logger.info("📍 Creating baseline for fresh database...");
                flyway.baseline();
            }

            if (analysis.failedMigrations() > 0) {
                logger.warn("⚠️  Found {} failed migrations. Attempting repair...",
                           analysis.failedMigrations());
                flyway.repair();
            }

            if (analysis.pendingMigrations() > 0) {
                logger.info("⬆️  Applying {} pending migrations...", analysis.pendingMigrations());
                var result = flyway.migrate();

                if (result.success) {
                    logger.info("✅ Successfully applied {} migrations", result.migrationsExecuted);
                } else {
                    throw new IllegalStateException("Migration execution failed");
                }
            } else {
                logger.info("✅ No pending migrations to apply");
            }

        } catch (FlywayException e) {
            logger.error("❌ Migration execution failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to execute database migrations", e);
        }
    }

    /**
     * Validates the final schema integrity.
     */
    private void validateSchemaIntegrity() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            Set<String> existingTables = new HashSet<>();

            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME").toLowerCase();
                    existingTables.add(tableName);
                }
            }

            // Verify all expected tables exist
            Set<String> missingTables = new HashSet<>(EXPECTED_TABLES);
            missingTables.removeAll(existingTables);

            if (!missingTables.isEmpty()) {
                logger.error("❌ Missing expected tables: {}", missingTables);
                throw new IllegalStateException("Schema validation failed: missing tables " + missingTables);
            }

            logger.info("✅ Schema integrity validation passed. All expected tables present.");

        } catch (SQLException e) {
            logger.error("❌ Schema integrity validation failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Schema validation failed", e);
        }
    }

    /**
     * Migration analysis result record.
     *
     * @param totalMigrations Total number of migrations
     * @param successfulMigrations Number of successful migrations
     * @param pendingMigrations Number of pending migrations
     * @param failedMigrations Number of failed migrations
     * @param hasBaseline Whether baseline exists
     */
    public record MigrationAnalysis(
        int totalMigrations,
        int successfulMigrations,
        int pendingMigrations,
        int failedMigrations,
        boolean hasBaseline
    ) {
        /**
         * Determines if migration is needed.
         *
         * @return true if migration is required
         */
        public boolean needsMigration() {
            return pendingMigrations > 0 ||
                   failedMigrations > 0 ||
                   (!hasBaseline && totalMigrations == 0);
        }
    }
}

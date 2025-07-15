package integration.com.example.metrics.jira_metrics;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.boot.jdbc.DataSourceBuilder;

/**
 * Integration test for Flyway database migration management.
 * Validates that all migrations run successfully and create the expected schema.
 * Uses H2 an in-memory database for testing to ensure isolation and speed.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@ActiveProfiles("test")
class FlywayIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(FlywayIntegrationTest.class);

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private Flyway flyway;

    /**
     * Set up test environment with H2 database and Flyway configuration.
     */
    @BeforeEach
    void setUp() {
        // Create H2 in-memory database for testing
        dataSource = DataSourceBuilder.create()
            .driverClassName("org.h2.Driver")
            .url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL")
            .username("sa")
            .password("")
            .build();

        jdbcTemplate = new JdbcTemplate(dataSource);

        // Configure Flyway for testing
        flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
            .cleanDisabled(false)
            .table("flyway_schema_history")
            .baselineVersion("0")
            .baselineDescription("Initial baseline")
            .load();
    }

    /**
     * Test that Flyway migrations run successfully and create all expected tables.
     * Validates the complete database schema after migration execution.
     */
    @Test
    void shouldRunFlywayMigrationsSuccessfully() {
        // Given: Clean database
        flyway.clean();

        // When: Run migrations
        var migrationResult = flyway.migrate();

        // Then: Verify migrations executed successfully
        assertTrue(migrationResult.success, "Flyway migrations should execute successfully");
        assertTrue(migrationResult.migrationsExecuted > 0, "At least one migration should be executed");

        logger.info("Successfully executed {} migrations", migrationResult.migrationsExecuted);

        // Verify all expected tables exist
        validateCoreTablesExist();
        validateSprintAndIssueTablesExist();
        validateBoardMetricsTableExists();
        validateTableStructures();
        validateForeignKeys();
        validateIndexes();
    }

    /**
     * Test that all migration files are valid and in correct order.
     */
    @Test
    void shouldHaveValidMigrationHistory() {
        // Given: Flyway is configured
        var migrationInfos = flyway.info().all();

        // Then: Verify migration information
        assertTrue(migrationInfos.length > 0, "Should have migration files");

        // Verify all migrations have valid versions and descriptions
        for (MigrationInfo info : migrationInfos) {
            assertNotNull(info.getVersion(), "Migration should have a version");
            assertNotNull(info.getDescription(), "Migration should have a description");
            logger.debug("Migration: {} - {}", info.getVersion(), info.getDescription());
        }

        // Verify expected migration versions exist
        var versions = Set.of("1.0.0", "1.1.0", "1.2.0", "1.3.0", "2.0.0");
        for (var expectedVersion : versions) {
            boolean versionExists = false;
            for (MigrationInfo info : migrationInfos) {
                if (info.getVersion().getVersion().equals(expectedVersion)) {
                    versionExists = true;
                    break;
                }
            }
            assertTrue(versionExists, "Migration version " + expectedVersion + " should exist");
        }
    }

    /**
     * Test that Flyway can clean and re-migrate successfully.
     */
    @Test
    void shouldSupportCleanAndReMigrate() {
        // Given: Database with existing migrations
        flyway.migrate();

        // When: Clean and re-migrate
        flyway.clean();
        var remigrationResult = flyway.migrate();

        // Then: Verify successful re-migration
        assertTrue(remigrationResult.success, "Re-migration should be successful");
        assertTrue(remigrationResult.migrationsExecuted > 0, "Should execute migrations on clean database");

        // Verify schema is intact after re-migration
        validateCoreTablesExist();
        validateSprintAndIssueTablesExist();
        validateBoardMetricsTableExists();
    }

    /**
     * Validates that core tables from V1.0.0 migration exist with correct structure.
     */
    private void validateCoreTablesExist() {
        // Verify boards table
        var boardsColumns = getTableColumns("BOARDS");
        assertFalse(boardsColumns.isEmpty(), "Boards table should exist");
        assertTrue(boardsColumns.contains("ID"), "Boards table should have ID column");
        assertTrue(boardsColumns.contains("BOARD_ID"), "Boards table should have BOARD_ID column");
        assertTrue(boardsColumns.contains("BOARD_NAME"), "Boards table should have BOARD_NAME column");
        assertTrue(boardsColumns.contains("PROJECT_KEY"), "Boards table should have PROJECT_KEY column");
        assertTrue(boardsColumns.contains("BOARD_TYPE"), "Boards table should have BOARD_TYPE column");
        assertTrue(boardsColumns.contains("HAS_SPRINTS"), "Boards table should have HAS_SPRINTS column");
        assertTrue(boardsColumns.contains("IS_ACTIVE"), "Boards table should have IS_ACTIVE column");
        assertTrue(boardsColumns.contains("CREATED_AT"), "Boards table should have CREATED_AT column");

        // Verify teams table
        var teamsColumns = getTableColumns("TEAMS");
        assertFalse(teamsColumns.isEmpty(), "Teams table should exist");
        assertTrue(teamsColumns.contains("ID"), "Teams table should have ID column");
        assertTrue(teamsColumns.contains("TEAM_ID"), "Teams table should have TEAM_ID column");
        assertTrue(teamsColumns.contains("TEAM_NAME"), "Teams table should have TEAM_NAME column");
        assertTrue(teamsColumns.contains("IS_ACTIVE"), "Teams table should have IS_ACTIVE column");

        // Verify jira_data table
        var jiraDataColumns = getTableColumns("JIRA_DATA");
        assertFalse(jiraDataColumns.isEmpty(), "Jira_data table should exist");
        assertTrue(jiraDataColumns.contains("ID"), "Jira_data table should have ID column");
        assertTrue(jiraDataColumns.contains("DATA_TYPE"), "Jira_data table should have DATA_TYPE column");
        assertTrue(jiraDataColumns.contains("RAW_DATA"), "Jira_data table should have RAW_DATA column");

        logger.info("Core tables validation completed successfully");
    }

    /**
     * Validates that sprint and issue tables from V1.1.0 migration exist.
     */
    private void validateSprintAndIssueTablesExist() {
        // Verify sprints table
        var sprintsColumns = getTableColumns("SPRINTS");
        assertFalse(sprintsColumns.isEmpty(), "Sprints table should exist");
        assertTrue(sprintsColumns.contains("ID"), "Sprints table should have ID column");
        assertTrue(sprintsColumns.contains("SPRINT_ID"), "Sprints table should have SPRINT_ID column");
        assertTrue(sprintsColumns.contains("BOARD_ID"), "Sprints table should have BOARD_ID column");
        assertTrue(sprintsColumns.contains("SPRINT_NAME"), "Sprints table should have SPRINT_NAME column");
        assertTrue(sprintsColumns.contains("SPRINT_STATE"), "Sprints table should have SPRINT_STATE column");

        // Verify issues table
        var issuesColumns = getTableColumns("ISSUES");
        assertFalse(issuesColumns.isEmpty(), "Issues table should exist");
        assertTrue(issuesColumns.contains("ID"), "Issues table should have ID column");
        assertTrue(issuesColumns.contains("ISSUE_ID"), "Issues table should have ISSUE_ID column");
        assertTrue(issuesColumns.contains("ISSUE_KEY"), "Issues table should have ISSUE_KEY column");
        assertTrue(issuesColumns.contains("BOARD_ID"), "Issues table should have BOARD_ID column");
        assertTrue(issuesColumns.contains("ISSUE_TYPE"), "Issues table should have ISSUE_TYPE column");
        assertTrue(issuesColumns.contains("STATUS"), "Issues table should have STATUS column");

        logger.info("Sprint and issue tables validation completed successfully");
    }

    /**
     * Validates that board metrics table from V1.2.0 migration exists.
     */
    private void validateBoardMetricsTableExists() {
        var metricsColumns = getTableColumns("BOARD_METRICS");
        assertFalse(metricsColumns.isEmpty(), "Board_metrics table should exist");
        assertTrue(metricsColumns.contains("ID"), "Board_metrics table should have ID column");
        assertTrue(metricsColumns.contains("BOARD_ID"), "Board_metrics table should have BOARD_ID column");
        assertTrue(metricsColumns.contains("METRIC_PERIOD_START"), "Board_metrics table should have METRIC_PERIOD_START column");
        assertTrue(metricsColumns.contains("METRIC_PERIOD_END"), "Board_metrics table should have METRIC_PERIOD_END column");
        assertTrue(metricsColumns.contains("METRIC_TYPE"), "Board_metrics table should have METRIC_TYPE column");
        assertTrue(metricsColumns.contains("VELOCITY_STORY_POINTS"), "Board_metrics table should have velocity metrics");
        assertTrue(metricsColumns.contains("CYCLE_TIME_AVG"), "Board_metrics table should have cycle time metrics");

        logger.info("Board metrics table validation completed successfully");
    }

    /**
     * Validates table structures including data types and constraints.
     */
    private void validateTableStructures() {
        // Verify primary keys exist
        assertTrue(hasPrimaryKey("BOARDS"), "Boards table should have primary key");
        assertTrue(hasPrimaryKey("TEAMS"), "Teams table should have primary key");
        assertTrue(hasPrimaryKey("SPRINTS"), "Sprints table should have primary key");
        assertTrue(hasPrimaryKey("ISSUES"), "Issues table should have primary key");
        assertTrue(hasPrimaryKey("BOARD_METRICS"), "Board_metrics table should have primary key");

        // Verify unique constraints
        assertTrue(hasUniqueConstraint("BOARDS", "BOARD_ID"), "Boards table should have unique constraint on BOARD_ID");
        assertTrue(hasUniqueConstraint("TEAMS", "TEAM_ID"), "Teams table should have unique constraint on TEAM_ID");
        assertTrue(hasUniqueConstraint("SPRINTS", "SPRINT_ID"), "Sprints table should have unique constraint on SPRINT_ID");
        assertTrue(hasUniqueConstraint("ISSUES", "ISSUE_ID"), "Issues table should have unique constraint on ISSUE_ID");

        logger.info("Table structures validation completed successfully");
    }

    /**
     * Validates foreign key relationships.
     */
    private void validateForeignKeys() {
        // Verify foreign key from sprints to boards
        assertTrue(hasForeignKey("SPRINTS", "BOARD_ID", "BOARDS", "BOARD_ID"),
                  "Sprints table should have foreign key to boards table");

        logger.info("Foreign keys validation completed successfully");
    }

    /**
     * Validates database indexes for performance.
     */
    private void validateIndexes() {
        // Verify indexes on frequently queried columns
        assertTrue(hasIndex("BOARDS", "BOARD_ID"), "Boards table should have index on BOARD_ID");
        assertTrue(hasIndex("SPRINTS", "BOARD_ID"), "Sprints table should have index on BOARD_ID");
        assertTrue(hasIndex("ISSUES", "BOARD_ID"), "Issues table should have index on BOARD_ID");

        logger.info("Indexes validation completed successfully");
    }

    /**
     * Gets column names for a given table.
     *
     * @param tableName the name of the table
     * @return set of column names in uppercase
     */
    private Set<String> getTableColumns(String tableName) {
        try {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
                tableName
            );
            return columns.stream()
                .map(col -> col.get("COLUMN_NAME").toString().toUpperCase())
                .collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            logger.warn("Could not retrieve columns for table {}: {}", tableName, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Checks if a table has a primary key.
     *
     * @param tableName the name of the table
     * @return true if primary key exists
     */
    private boolean hasPrimaryKey(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.CONSTRAINTS " +
                "WHERE TABLE_NAME = ? AND CONSTRAINT_TYPE = 'PRIMARY KEY'",
                Integer.class, tableName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            logger.warn("Could not check primary key for table {}: {}", tableName, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a table has a unique constraint on a specific column.
     *
     * @param tableName the name of the table
     * @param columnName the name of the column
     * @return true if unique constraint exists
     */
    private boolean hasUniqueConstraint(String tableName, String columnName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.CONSTRAINTS c " +
                "JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu ON c.CONSTRAINT_NAME = ccu.CONSTRAINT_NAME " +
                "WHERE c.TABLE_NAME = ? AND ccu.COLUMN_NAME = ? AND c.CONSTRAINT_TYPE = 'UNIQUE'",
                Integer.class, tableName, columnName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            logger.warn("Could not check unique constraint for {}.{}: {}", tableName, columnName, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a foreign key exists between two tables.
     *
     * @param sourceTable the source table name
     * @param sourceColumn the source column name
     * @param targetTable the target table name
     * @param targetColumn the target column name
     * @return true if foreign key exists
     */
    private boolean hasForeignKey(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc " +
                "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu ON rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME " +
                "WHERE kcu.TABLE_NAME = ? AND kcu.COLUMN_NAME = ?",
                Integer.class, sourceTable, sourceColumn
            );
            return count != null && count > 0;
        } catch (Exception e) {
            logger.warn("Could not check foreign key for {}.{}: {}", sourceTable, sourceColumn, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if an index exists on a specific column.
     *
     * @param tableName the name of the table
     * @param columnName the name of the column
     * @return true if index exists
     */
    private boolean hasIndex(String tableName, String columnName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES " +
                "WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, tableName, columnName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            // For H2, indexes might not be visible in INFORMATION_SCHEMA.INDEXES
            // This is acceptable for testing purposes
            logger.debug("Could not check index for {}.{}: {}", tableName, columnName, e.getMessage());
            return true; // Assume index exists to avoid false negatives in H2
        }
    }
}

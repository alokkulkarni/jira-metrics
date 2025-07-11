package integration.com.example.metrics.jira_metrics;

import com.example.metrics.jira_metrics.JiraMetricsApplication;
import com.example.metrics.jira_metrics.entity.Board;
import com.example.metrics.jira_metrics.repository.BoardRepository;
import com.example.metrics.jira_metrics.repository.JiraDataRepository;
import com.example.metrics.jira_metrics.repository.TeamRepository;
import com.example.metrics.jira_metrics.test.config.BaseTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for repository layer with real PostgreSQL database using TestContainers.
 * Tests repository operations against a real database instance in an isolated environment.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@SpringBootTest(classes = JiraMetricsApplication.class)
@ActiveProfiles("test")
@DisplayName("Repository Integration Tests with TestContainers")
class RepositoryIntegrationTest extends BaseTestContainersConfig {

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private JiraDataRepository jiraDataRepository;

    /**
     * Sets up clean database state before each test.
     * Ensures test isolation by clearing all data.
     */
    @BeforeEach
    void setUp() {
        // Clean up data before each test to ensure isolation
        jiraDataRepository.deleteAll();
        teamRepository.deleteAll();
        boardRepository.deleteAll();

        assertTrue(isDatabaseReady(), "Database container should be ready for testing");
    }

    /**
     * Tests basic CRUD operations for Board entity with PostgreSQL TestContainer.
     */
    @Test
    @DisplayName("Should perform CRUD operations on Board entity successfully")
    void boardCrudOperations_WithPostgreSQLContainer_ShouldWork() {
        // Given
        var testBoard = new Board(123L, "Test Board", "TEST");

        // When - Create
        var savedBoard = boardRepository.save(testBoard);

        // Then - Verify creation
        assertNotNull(savedBoard.getId());
        assertEquals("Test Board", savedBoard.getBoardName());
        assertTrue(savedBoard.getIsActive());

        // When - Read
        var retrievedBoard = boardRepository.findByBoardId(123L);

        // Then - Verify retrieval
        assertTrue(retrievedBoard.isPresent());
        assertEquals("Test Board", retrievedBoard.get().getBoardName());
        assertEquals("TEST", retrievedBoard.get().getProjectKey());

        // When - Update
        retrievedBoard.get().setBoardName("Updated Board");
        var updatedBoard = boardRepository.save(retrievedBoard.get());

        // Then - Verify update
        assertEquals("Updated Board", updatedBoard.getBoardName());

        // When - Delete
        boardRepository.delete(updatedBoard);
        var deletedBoard = boardRepository.findByBoardId(123L);

        // Then - Verify deletion
        assertTrue(deletedBoard.isEmpty());
    }

    /**
     * Tests complex query operations with multiple boards.
     */
    @Test
    @DisplayName("Should find active boards correctly with complex queries")
    void findActiveBoards_WithMixedBoardStates_ShouldReturnOnlyActive() {
        // Given - Create boards with different states
        var activeBoard1 = new Board(123L, "Active Board 1", "ACT1");
        var activeBoard2 = new Board(456L, "Active Board 2", "ACT2");
        var inactiveBoard = new Board(789L, "Inactive Board", "INACT");
        inactiveBoard.setIsActive(false);

        boardRepository.save(activeBoard1);
        boardRepository.save(activeBoard2);
        boardRepository.save(inactiveBoard);

        // When - Query for active boards
        var activeBoards = boardRepository.findAllActiveBoards();

        // Then - Verify only active boards are returned
        assertEquals(2, activeBoards.size());
        assertTrue(activeBoards.stream().allMatch(Board::getIsActive));
        assertTrue(activeBoards.stream()
                .anyMatch(board -> "Active Board 1".equals(board.getBoardName())));
        assertTrue(activeBoards.stream()
                .anyMatch(board -> "Active Board 2".equals(board.getBoardName())));
    }

    /**
     * Tests project-specific board queries.
     */
    @Test
    @DisplayName("Should find boards by project key and active status")
    void findBoardsByProject_WithVariousProjects_ShouldReturnCorrectBoards() {
        // Given - Create boards for different projects
        var projectBoard1 = new Board(123L, "Project A Board 1", "PROJA");
        var projectBoard2 = new Board(456L, "Project A Board 2", "PROJA");
        var inactiveProjectBoard = new Board(789L, "Inactive Project A Board", "PROJA");
        inactiveProjectBoard.setIsActive(false);
        var differentProjectBoard = new Board(999L, "Project B Board", "PROJB");

        boardRepository.save(projectBoard1);
        boardRepository.save(projectBoard2);
        boardRepository.save(inactiveProjectBoard);
        boardRepository.save(differentProjectBoard);

        // When - Query for active boards in Project A
        var projectABoards = boardRepository.findByProjectKeyAndIsActiveTrue("PROJA");

        // Then - Verify correct boards returned
        assertEquals(2, projectABoards.size());
        assertTrue(projectABoards.stream().allMatch(Board::getIsActive));
        assertTrue(projectABoards.stream()
                .allMatch(board -> "PROJA".equals(board.getProjectKey())));
    }

    /**
     * Tests database connectivity and schema creation with TestContainers.
     */
    @Test
    @DisplayName("Should verify database schema and connectivity")
    void databaseConnectivity_WithTestContainers_ShouldWorkCorrectly() {
        // Given - TestContainer should be running
        assertTrue(getPostgresContainer().isRunning(),
                "PostgreSQL container should be running");

        // When - Check repository counts
        var boardCount = boardRepository.count();
        var teamCount = teamRepository.count();
        var jiraDataCount = jiraDataRepository.count();

        // Then - Verify clean database state
        assertEquals(0, boardCount, "Board repository should be empty initially");
        assertEquals(0, teamCount, "Team repository should be empty initially");
        assertEquals(0, jiraDataCount, "JIRA data repository should be empty initially");
    }

    /**
     * Tests concurrent operations to ensure thread safety with TestContainers.
     */
    @Test
    @DisplayName("Should handle concurrent database operations safely")
    void concurrentOperations_WithTestContainers_ShouldBeSafe() {
        // Given - Multiple boards to save concurrently
        var boards = List.of(
                new Board(100L, "Concurrent Board 1", "CONC1"),
                new Board(200L, "Concurrent Board 2", "CONC2"),
                new Board(300L, "Concurrent Board 3", "CONC3")
        );

        // When - Save boards in parallel
        boards.parallelStream().forEach(boardRepository::save);

        // Then - Verify all boards are saved correctly
        var savedBoards = boardRepository.findAllActiveBoards();
        assertEquals(3, savedBoards.size());

        // Verify each board is present
        assertTrue(savedBoards.stream()
                .anyMatch(board -> "Concurrent Board 1".equals(board.getBoardName())));
        assertTrue(savedBoards.stream()
                .anyMatch(board -> "Concurrent Board 2".equals(board.getBoardName())));
        assertTrue(savedBoards.stream()
                .anyMatch(board -> "Concurrent Board 3".equals(board.getBoardName())));
    }
}

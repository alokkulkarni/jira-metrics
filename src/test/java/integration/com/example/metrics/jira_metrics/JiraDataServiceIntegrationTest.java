package integration.com.example.metrics.jira_metrics;

import com.example.metrics.jira_metrics.JiraMetricsApplication;
import com.example.metrics.jira_metrics.entity.Board;
import com.example.metrics.jira_metrics.entity.JiraData;
import com.example.metrics.jira_metrics.entity.Team;
import com.example.metrics.jira_metrics.repository.BoardRepository;
import com.example.metrics.jira_metrics.repository.JiraDataRepository;
import com.example.metrics.jira_metrics.repository.TeamRepository;
import com.example.metrics.jira_metrics.service.JiraDataService;
import com.example.metrics.jira_metrics.test.config.BaseTestContainersConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Full-stack integration tests for JIRA data processing with TestContainers.
 * Tests the complete data flow from JIRA API simulation to database persistence.
 * Uses MockWebServer to simulate JIRA API responses and PostgreSQL TestContainer for data persistence.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@SpringBootTest(classes = JiraMetricsApplication.class)
@ActiveProfiles("test")
@DisplayName("JIRA Data Service Integration Tests with TestContainers")
class JiraDataServiceIntegrationTest extends BaseTestContainersConfig {

    private static MockWebServer mockJiraServer;

    @Autowired
    private JiraDataService jiraDataService;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private JiraDataRepository jiraDataRepository;

    /**
     * Configures the mock JIRA server URL for testing.
     * This replaces the real JIRA base URL with our MockWebServer.
     *
     * @param registry the dynamic property registry to configure
     */
    @DynamicPropertySource
    static void configureMockJiraServer(DynamicPropertyRegistry registry) throws IOException {
        mockJiraServer = new MockWebServer();
        mockJiraServer.start();

        var mockServerUrl = mockJiraServer.url("/").toString();
        // Remove trailing slash to match expected format
        String baseUrl = mockServerUrl.endsWith("/") ? mockServerUrl.substring(0, mockServerUrl.length() - 1) : mockServerUrl;
        registry.add("jira.base-url", () -> baseUrl);
        registry.add("jira.username", () -> "test@example.com");
        registry.add("jira.api-token", () -> "test-token");
    }

    /**
     * Sets up clean state before each test.
     * Clears database and prepares mock server responses.
     */
    @BeforeEach
    void setUp() {
        // Clean database state
        jiraDataRepository.deleteAll();
        teamRepository.deleteAll();
        boardRepository.deleteAll();

        assertTrue(isDatabaseReady(), "Database container should be ready");
        assertTrue(mockJiraServer != null, "Mock JIRA server should be initialized");
    }

    /**
     * Cleans up resources after each test.
     */
    @AfterEach
    void tearDown() throws IOException {
        // Clear any remaining mock responses to avoid interference between tests
        while (mockJiraServer.getRequestCount() > 0) {
            try {
                mockJiraServer.takeRequest(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (mockJiraServer != null) {
            mockJiraServer.shutdown();
        }
    }

    /**
     * Tests complete board data processing flow with TestContainers.
     * Simulates JIRA API responses and verifies data is correctly stored in PostgreSQL.
     */
    @Test
    @DisplayName("Should process board data end-to-end with TestContainers")
    void processBoardData_WithMockJiraAndTestContainers_ShouldPersistCorrectly() {
        // Given - Setup test board
        var testBoard = new Board(123L, "Test Board", "TEST");
        boardRepository.save(testBoard);

        // Mock JIRA API responses
        enqueueMockJiraResponses();

        // When - Process board data
        jiraDataService.processBoardData(testBoard);

        // Then - Verify data is persisted correctly
        var savedJiraData = jiraDataRepository.findByBoardIdAndDataType(123L, "board_config");
        assertFalse(savedJiraData.isEmpty(), "Board configuration data should be saved");

        var configData = savedJiraData.get(0);
        assertEquals(123L, configData.getBoardId());
        assertEquals("board_config", configData.getDataType());
        assertNotNull(configData.getRawData());
        assertNotNull(configData.getRetrievalTimestamp());

        // Verify issues data
        var issuesData = jiraDataRepository.findByBoardIdAndDataType(123L, "issues");
        assertFalse(issuesData.isEmpty(), "Issues data should be saved");

        // Verify sprints data
        var sprintsData = jiraDataRepository.findByBoardIdAndDataType(123L, "sprints");
        assertFalse(sprintsData.isEmpty(), "Sprints data should be saved");
    }

    /**
     * Tests team data processing with TestContainers.
     */
    @Test
    @DisplayName("Should process team data and create team entities with TestContainers")
    void processTeamData_WithMockJiraAndTestContainers_ShouldCreateTeamEntities() {
        // Given - Setup mock team data response
        String teamResponse = """
            {
                "values": [
                    {
                        "id": "team-123",
                        "name": "Development Team",
                        "description": "Main development team",
                        "lead": {
                            "accountId": "lead-123",
                            "displayName": "John Doe"
                        },
                        "members": {
                            "size": 5
                        }
                    }
                ]
            }
            """;

        mockJiraServer.enqueue(new MockResponse()
                .setBody(teamResponse)
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        // When - Process team data
        assertDoesNotThrow(() -> jiraDataService.processTeamData());

        // Then - Verify team entity is created with more lenient timing
        await().atMost(10, TimeUnit.SECONDS)
               .pollInterval(500, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
            var allTeams = (List<Team>) teamRepository.findAll();
            var activeTeams = allTeams.stream()
                    .filter(team -> Boolean.TRUE.equals(team.getIsActive()))
                    .toList();

            assertEquals(1, activeTeams.size(), "Exactly one team should be created");

            var team = activeTeams.get(0);
            assertEquals("team-123", team.getTeamId());
            assertEquals("Development Team", team.getTeamName());
            assertEquals("Main development team", team.getDescription());
            assertEquals("lead-123", team.getLeadAccountId());
            assertEquals("John Doe", team.getLeadDisplayName());
            assertEquals(5, team.getMemberCount());
        });

        // Verify raw team data is also stored
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var allJiraData = (List<JiraData>) jiraDataRepository.findAll();
            var teamData = allJiraData.stream()
                    .filter(data -> "teams".equals(data.getDataType()))
                    .toList();

            assertFalse(teamData.isEmpty(), "Raw team data should be stored");
        });
    }

    /**
     * Tests error handling with TestContainers when JIRA API fails.
     */
    @Test
    @DisplayName("Should handle JIRA API errors gracefully with TestContainers")
    void processBoardData_WithJiraApiErrors_ShouldHandleGracefully() {
        // Given - Setup test board
        var testBoard = new Board(456L, "Error Board", "ERR");
        boardRepository.save(testBoard);

        // Mock JIRA API error responses
        mockJiraServer.enqueue(new MockResponse().setResponseCode(404));
        mockJiraServer.enqueue(new MockResponse().setResponseCode(500));
        mockJiraServer.enqueue(new MockResponse().setResponseCode(403));

        // When - Process board data (should not throw exceptions)
        assertDoesNotThrow(() -> jiraDataService.processBoardData(testBoard));

        // Then - Verify no data is stored due to errors
        var savedData = jiraDataRepository.findByBoardIdAndDataType(456L, "board_config");
        assertTrue(savedData.isEmpty(), "No data should be saved when API calls fail");
    }

    /**
     * Tests concurrent processing with multiple boards using TestContainers.
     */
    @Test
    @DisplayName("Should handle concurrent board processing with TestContainers")
    void processMultipleBoards_Concurrently_ShouldHandleCorrectly() {
        // Given - Create multiple test boards
        var boards = List.of(
                new Board(100L, "Board 1", "PROJ1"),
                new Board(200L, "Board 2", "PROJ2"),
                new Board(300L, "Board 3", "PROJ3")
        );

        boards.forEach(boardRepository::save);

        // Mock responses for all boards (3 boards × 3 API calls each = 9 total)
        for (int i = 0; i < 9; i++) {
            enqueueMockJiraResponse();
        }

        // When - Process boards concurrently
        boards.parallelStream().forEach(jiraDataService::processBoardData);

        // Then - Verify all data is stored correctly (fixed assertion - should expect data to be saved)
        boards.forEach(board -> {
            var boardData = jiraDataRepository.findByBoardIdAndDataType(
                    board.getBoardId(), "board_config");
            assertFalse(boardData.isEmpty(),
                    "Data should be saved for board " + board.getBoardId());
        });
    }

    /**
     * Tests data retrieval methods with TestContainers.
     */
    @Test
    @DisplayName("Should retrieve latest board data from TestContainer database")
    void getLatestBoardData_WithTestContainers_ShouldReturnCorrectData() {
        // Given - Create test data with different timestamps
        var olderData = new JiraData(123L, "issues", "{\"old\": true}",
                LocalDateTime.now().minusHours(2));
        var newerData = new JiraData(123L, "issues", "{\"new\": true}",
                LocalDateTime.now());

        jiraDataRepository.save(olderData);
        jiraDataRepository.save(newerData);

        // When - Get latest data
        var latestData = jiraDataService.getLatestBoardData(123L, "issues");

        // Then - Verify latest data is returned
        assertTrue(latestData.isPresent());
        assertTrue(latestData.get().getRawData().contains("new"));
        assertFalse(latestData.get().getRawData().contains("old"));
    }

    /**
     * Enqueues mock JIRA API responses for testing.
     * Provides realistic JSON responses for board configuration, issues, and sprints.
     */
    private void enqueueMockJiraResponses() {
        // Board configuration response
        mockJiraServer.enqueue(new MockResponse()
                .setBody("{\"id\": 123, \"name\": \"Test Board\", \"type\": \"scrum\"}")
                .addHeader("Content-Type", "application/json"));

        // Issues response
        mockJiraServer.enqueue(new MockResponse()
                .setBody("""
                    {
                        "startAt": 0,
                        "maxResults": 50,
                        "total": 2,
                        "issues": [
                            {"id": "ISSUE-1", "key": "TEST-1"},
                            {"id": "ISSUE-2", "key": "TEST-2"}
                        ]
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        // Sprints response
        mockJiraServer.enqueue(new MockResponse()
                .setBody("""
                    {
                        "values": [
                            {"id": 1, "name": "Sprint 1", "state": "active"},
                            {"id": 2, "name": "Sprint 2", "state": "closed"}
                        ]
                    }
                    """)
                .addHeader("Content-Type", "application/json"));
    }

    /**
     * Enqueues a single mock JIRA API response for concurrent testing.
     */
    private void enqueueMockJiraResponse() {
        mockJiraServer.enqueue(new MockResponse()
                .setBody("{\"id\": 1, \"data\": \"test\"}")
                .addHeader("Content-Type", "application/json"));
    }
}

package unit.com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.service.JiraClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JiraClientService.
 * Tests HTTP client interactions with JIRA REST API using MockWebServer.
 */
@DisplayName("JiraClientService Unit Tests")
class JiraClientServiceTest {

    private static final Long TEST_BOARD_ID = 123L;
    private static final String TEST_TEAM_ID = "team-123";
    private static final String TEST_USERNAME = "test@example.com";
    private static final String TEST_API_TOKEN = "test-token";

    private MockWebServer mockWebServer;
    private JiraClientService jiraClientService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        objectMapper = new ObjectMapper();
        String baseUrl = mockWebServer.url("/").toString();

        jiraClientService = new JiraClientService(
            baseUrl, TEST_USERNAME, TEST_API_TOKEN, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Should retrieve board configuration successfully")
    void getBoardConfiguration_WithValidBoardId_ShouldReturnConfiguration() throws InterruptedException {
        // Given
        String responseJson = """
            {
                "id": 123,
                "name": "Test Board",
                "type": "scrum"
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        // When
        Optional<JsonNode> result = jiraClientService.getBoardConfiguration(TEST_BOARD_ID);

        // Then
        assertTrue(result.isPresent());
        assertEquals(123, result.get().path("id").asInt());
        assertEquals("Test Board", result.get().path("name").asText());

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().contains("/rest/agile/1.0/board/123/configuration"));
        assertNotNull(request.getHeader("Authorization"));
    }

    @Test
    @DisplayName("Should handle HTTP 404 error gracefully")
    void getBoardConfiguration_WithNotFoundError_ShouldReturnEmpty() throws InterruptedException {
        // Given
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        // When
        Optional<JsonNode> result = jiraClientService.getBoardConfiguration(TEST_BOARD_ID);

        // Then
        assertFalse(result.isPresent());

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
    }

    @Test
    @DisplayName("Should retrieve board issues with pagination parameters")
    void getBoardIssues_WithPaginationParams_ShouldIncludeInUrl() throws InterruptedException {
        // Given
        String responseJson = """
            {
                "startAt": 0,
                "maxResults": 50,
                "total": 100,
                "issues": []
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        // When
        Optional<JsonNode> result = jiraClientService.getBoardIssues(TEST_BOARD_ID, 0, 50);

        // Then
        assertTrue(result.isPresent());
        assertEquals(0, result.get().path("startAt").asInt());
        assertEquals(50, result.get().path("maxResults").asInt());

        RecordedRequest request = mockWebServer.takeRequest();
        String path = request.getPath();
        assertTrue(path.contains("startAt=0"));
        assertTrue(path.contains("maxResults=50"));
    }

    @Test
    @DisplayName("Should retrieve board sprints successfully")
    void getBoardSprints_WithValidBoardId_ShouldReturnSprints() throws InterruptedException {
        // Given
        String responseJson = """
            {
                "maxResults": 50,
                "startAt": 0,
                "total": 2,
                "values": [
                    {"id": 1, "name": "Sprint 1"},
                    {"id": 2, "name": "Sprint 2"}
                ]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        // When
        Optional<JsonNode> result = jiraClientService.getBoardSprints(TEST_BOARD_ID);

        // Then
        assertTrue(result.isPresent());
        assertEquals(2, result.get().path("values").size());

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().contains("/rest/agile/1.0/board/123/sprint"));
    }

    @Test
    @DisplayName("Should retrieve team details successfully")
    void getTeamDetails_WithValidTeamId_ShouldReturnTeamInfo() throws InterruptedException {
        // Given
        String responseJson = """
            {
                "id": "team-123",
                "name": "Development Team",
                "description": "Main development team"
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        // When
        Optional<JsonNode> result = jiraClientService.getTeamDetails(TEST_TEAM_ID);

        // Then
        assertTrue(result.isPresent());
        assertEquals(TEST_TEAM_ID, result.get().path("id").asText());
        assertEquals("Development Team", result.get().path("name").asText());

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().contains("/rest/teams/1.0/teams/team-123"));
    }

    @Test
    @DisplayName("Should retrieve all teams successfully")
    void getAllTeams_WithValidResponse_ShouldReturnTeamsList() throws InterruptedException {
        // Given
        String responseJson = """
            {
                "values": [
                    {"id": "team-1", "name": "Team Alpha"},
                    {"id": "team-2", "name": "Team Beta"}
                ],
                "size": 2
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        // When
        Optional<JsonNode> result = jiraClientService.getAllTeams();

        // Then
        assertTrue(result.isPresent());
        assertEquals(2, result.get().path("values").size());

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().contains("/rest/teams/1.0/teams"));
    }

    @Test
    @DisplayName("Should handle malformed JSON response gracefully")
    void getBoardConfiguration_WithMalformedJson_ShouldReturnEmpty() throws InterruptedException {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setBody("invalid json")
            .addHeader("Content-Type", "application/json"));

        // When
        Optional<JsonNode> result = jiraClientService.getBoardConfiguration(TEST_BOARD_ID);

        // Then
        assertFalse(result.isPresent());

        RecordedRequest request = mockWebServer.takeRequest();
        assertNotNull(request);
    }

    @Test
    @DisplayName("Should include proper authentication headers")
    void getAllRequests_ShouldIncludeBasicAuthHeader() throws InterruptedException {
        // Given
        mockWebServer.enqueue(new MockResponse().setBody("{}"));

        // When
        jiraClientService.getBoardConfiguration(TEST_BOARD_ID);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        String authHeader = request.getHeader("Authorization");
        assertNotNull(authHeader);
        assertTrue(authHeader.startsWith("Basic "));

        // Verify content type headers
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals("application/json", request.getHeader("Accept"));
    }
}

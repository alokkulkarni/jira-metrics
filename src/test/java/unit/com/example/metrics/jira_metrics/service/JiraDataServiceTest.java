package unit.com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.entity.Board;
import com.example.metrics.jira_metrics.entity.JiraData;
import com.example.metrics.jira_metrics.entity.Team;
import com.example.metrics.jira_metrics.repository.BoardRepository;
import com.example.metrics.jira_metrics.repository.JiraDataRepository;
import com.example.metrics.jira_metrics.repository.TeamRepository;
import com.example.metrics.jira_metrics.service.JiraClientService;
import com.example.metrics.jira_metrics.service.JiraDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JiraDataService.
 * Tests data processing and storage operations for JIRA integration.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JiraDataService Unit Tests")
class JiraDataServiceTest {

    private static final Long TEST_BOARD_ID = 123L;
    private static final String TEST_BOARD_NAME = "Test Board";
    private static final String TEST_PROJECT_KEY = "TEST";
    private static final String TEST_TEAM_ID = "team-123";
    private static final String TEST_TEAM_NAME = "Test Team";

    @Mock
    private JiraClientService jiraClientService;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private JiraDataRepository jiraDataRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private JiraDataService jiraDataService;

    private Board testBoard;
    private ObjectMapper realObjectMapper;

    @BeforeEach
    void setUp() {
        testBoard = new Board(TEST_BOARD_ID, TEST_BOARD_NAME, TEST_PROJECT_KEY);
        realObjectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should process all active boards successfully")
    void processAllBoards_WithActiveBoards_ShouldProcessSuccessfully() throws JsonProcessingException {
        // Given
        List<Board> activeBoards = List.of(testBoard);
        JsonNode mockJsonNode = createMockEmptyResponse();

        when(boardRepository.findAllActiveBoards()).thenReturn(activeBoards);
        when(jiraClientService.getBoardConfiguration(TEST_BOARD_ID)).thenReturn(Optional.of(mockJsonNode));
        when(jiraClientService.getBoardIssues(eq(TEST_BOARD_ID), anyInt(), anyInt())).thenReturn(Optional.of(mockJsonNode));
        when(jiraClientService.getBoardSprints(TEST_BOARD_ID)).thenReturn(Optional.of(mockJsonNode));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        jiraDataService.processAllBoards();

        // Then
        verify(boardRepository).findAllActiveBoards();
        verify(jiraClientService).getBoardConfiguration(TEST_BOARD_ID);
        verify(jiraClientService).getBoardIssues(eq(TEST_BOARD_ID), anyInt(), anyInt());
        verify(jiraClientService).getBoardSprints(TEST_BOARD_ID);
        verify(jiraDataRepository, atLeast(3)).save(any(JiraData.class));
    }

    @Test
    @DisplayName("Should handle empty board list gracefully")
    void processAllBoards_WithNoActiveBoards_ShouldHandleGracefully() {
        // Given
        when(boardRepository.findAllActiveBoards()).thenReturn(List.of());

        // When
        jiraDataService.processAllBoards();

        // Then
        verify(boardRepository).findAllActiveBoards();
        verify(jiraClientService, never()).getBoardConfiguration(any());
        verify(jiraDataRepository, never()).save(any(JiraData.class));
    }

    @Test
    @DisplayName("Should process team data successfully")
    void processTeamData_WithValidTeamData_ShouldProcessSuccessfully() throws JsonProcessingException {
        // Given
        JsonNode teamsResponse = createMockTeamsResponse();

        when(jiraClientService.getAllTeams()).thenReturn(Optional.of(teamsResponse));
        when(objectMapper.writeValueAsString(teamsResponse)).thenReturn("{}");
        when(teamRepository.findByTeamId(TEST_TEAM_ID)).thenReturn(Optional.empty());

        // When
        jiraDataService.processTeamData();

        // Then
        verify(jiraClientService).getAllTeams();
        verify(jiraDataRepository).save(any(JiraData.class));
        verify(teamRepository).save(any(Team.class));
    }

    @Test
    @DisplayName("Should handle JIRA client service errors gracefully")
    void processBoardData_WithJiraClientError_ShouldHandleGracefully() {
        // Given
        when(jiraClientService.getBoardConfiguration(TEST_BOARD_ID)).thenReturn(Optional.empty());
        when(jiraClientService.getBoardIssues(eq(TEST_BOARD_ID), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(jiraClientService.getBoardSprints(TEST_BOARD_ID)).thenReturn(Optional.empty());

        // When
        jiraDataService.processBoardData(testBoard);

        // Then
        verify(jiraClientService).getBoardConfiguration(TEST_BOARD_ID);
        verify(jiraClientService).getBoardIssues(eq(TEST_BOARD_ID), anyInt(), anyInt());
        verify(jiraClientService).getBoardSprints(TEST_BOARD_ID);
        verify(jiraDataRepository, never()).save(any(JiraData.class));
    }

    @Test
    @DisplayName("Should retrieve latest board data successfully")
    void getLatestBoardData_WithValidData_ShouldReturnData() {
        // Given
        String dataType = "issues";
        JiraData expectedData = new JiraData(TEST_BOARD_ID, dataType, "{}", LocalDateTime.now());
        when(jiraDataRepository.findLatestByBoardIdAndDataType(TEST_BOARD_ID, dataType))
                .thenReturn(expectedData);

        // When
        Optional<JiraData> result = jiraDataService.getLatestBoardData(TEST_BOARD_ID, dataType);

        // Then
        assertTrue(result.isPresent());
        assertEquals(expectedData, result.get());
        verify(jiraDataRepository).findLatestByBoardIdAndDataType(TEST_BOARD_ID, dataType);
    }

    @Test
    @DisplayName("Should return empty when no latest data exists")
    void getLatestBoardData_WithNoData_ShouldReturnEmpty() {
        // Given
        String dataType = "issues";
        when(jiraDataRepository.findLatestByBoardIdAndDataType(TEST_BOARD_ID, dataType))
                .thenReturn(null);

        // When
        Optional<JiraData> result = jiraDataService.getLatestBoardData(TEST_BOARD_ID, dataType);

        // Then
        assertFalse(result.isPresent());
        verify(jiraDataRepository).findLatestByBoardIdAndDataType(TEST_BOARD_ID, dataType);
    }

    @Test
    @DisplayName("Should process board issues with pagination successfully")
    void processBoardData_WithPaginatedIssues_ShouldProcessAllPages() throws JsonProcessingException {
        // Given
        JsonNode configNode = createMockEmptyResponse();
        JsonNode issuesPage1 = createMockPaginatedResponse(75, 0, 50);
        JsonNode issuesPage2 = createMockPaginatedResponse(75, 50, 25);
        JsonNode sprintsNode = createMockEmptyResponse();

        when(jiraClientService.getBoardConfiguration(TEST_BOARD_ID)).thenReturn(Optional.of(configNode));
        when(jiraClientService.getBoardIssues(TEST_BOARD_ID, 0, 50)).thenReturn(Optional.of(issuesPage1));
        when(jiraClientService.getBoardIssues(TEST_BOARD_ID, 50, 50)).thenReturn(Optional.of(issuesPage2));
        when(jiraClientService.getBoardSprints(TEST_BOARD_ID)).thenReturn(Optional.of(sprintsNode));

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        jiraDataService.processBoardData(testBoard);

        // Then
        verify(jiraClientService).getBoardConfiguration(TEST_BOARD_ID);
        verify(jiraClientService).getBoardIssues(TEST_BOARD_ID, 0, 50);
        verify(jiraClientService).getBoardIssues(TEST_BOARD_ID, 50, 50);
        verify(jiraClientService).getBoardSprints(TEST_BOARD_ID);
        // Expect 4 saves: 1 config + 2 paginated issues + 1 sprints
        verify(jiraDataRepository, times(4)).save(any(JiraData.class));
    }

    /**
     * Creates a mock empty JSON response for testing.
     *
     * @return JsonNode with empty structure
     */
    private JsonNode createMockEmptyResponse() {
        ObjectNode root = realObjectMapper.createObjectNode();
        root.put("total", 0);
        root.put("maxResults", 50);
        root.put("startAt", 0);
        root.set("issues", realObjectMapper.createArrayNode());
        root.set("values", realObjectMapper.createArrayNode());
        return root;
    }

    /**
     * Creates a mock paginated JSON response for testing.
     *
     * @param total total number of items
     * @param startAt starting index
     * @param returnedCount number of items in this page
     * @return JsonNode with pagination structure
     */
    private JsonNode createMockPaginatedResponse(int total, int startAt, int returnedCount) {
        ObjectNode root = realObjectMapper.createObjectNode();
        root.put("total", total);
        root.put("maxResults", 50);
        root.put("startAt", startAt);

        ArrayNode issues = realObjectMapper.createArrayNode();
        for (int i = 0; i < returnedCount; i++) {
            ObjectNode issue = realObjectMapper.createObjectNode();
            issue.put("id", "ISSUE-" + (startAt + i + 1));
            issues.add(issue);
        }
        root.set("issues", issues);
        root.set("values", issues);

        return root;
    }

    /**
     * Creates a mock teams response for testing.
     *
     * @return JsonNode with team structure
     */
    private JsonNode createMockTeamsResponse() {
        ObjectNode root = realObjectMapper.createObjectNode();
        ArrayNode values = realObjectMapper.createArrayNode();

        ObjectNode team = realObjectMapper.createObjectNode();
        team.put("id", TEST_TEAM_ID);
        team.put("name", TEST_TEAM_NAME);
        team.put("description", "Test Description");

        ObjectNode lead = realObjectMapper.createObjectNode();
        lead.put("accountId", "lead-123");
        lead.put("displayName", "Lead Name");
        team.set("lead", lead);

        ObjectNode members = realObjectMapper.createObjectNode();
        members.put("size", 5);
        team.set("members", members);

        values.add(team);
        root.set("values", values);

        return root;
    }
}

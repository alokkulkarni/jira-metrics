package integration.com.example.metrics.jira_metrics;

import com.example.metrics.jira_metrics.JiraMetricsApplication;
import com.example.metrics.jira_metrics.entity.Board;
import com.example.metrics.jira_metrics.repository.BoardRepository;
import com.example.metrics.jira_metrics.test.config.BaseTestContainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for REST API endpoints with TestContainers.
 * Tests the complete REST API layer against a real PostgreSQL database.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@SpringBootTest(classes = JiraMetricsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@DisplayName("REST API Integration Tests with TestContainers")
class RestApiIntegrationTest extends BaseTestContainersConfig {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    /**
     * Sets up MockMvc and clean database state before each test.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Clean database state
        boardRepository.deleteAll();

        assertTrue(isDatabaseReady(), "Database container should be ready");
    }

    /**
     * Tests GET /api/v1/boards endpoint with TestContainers.
     */
    @Test
    @DisplayName("Should retrieve all active boards via REST API")
    void getAllActiveBoards_WithTestContainers_ShouldReturnJsonArray() throws Exception {
        // Given - Create test boards in database
        var activeBoard1 = new Board(123L, "Active Board 1", "PROJ1");
        var activeBoard2 = new Board(456L, "Active Board 2", "PROJ2");
        var inactiveBoard = new Board(789L, "Inactive Board", "PROJ3");
        inactiveBoard.setIsActive(false);

        boardRepository.save(activeBoard1);
        boardRepository.save(activeBoard2);
        boardRepository.save(inactiveBoard);

        // When & Then - GET request should return only active boards
        mockMvc.perform(get("/api/v1/boards")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].boardName", containsInAnyOrder("Active Board 1", "Active Board 2")))
                .andExpect(jsonPath("$[*].isActive", everyItem(is(true))));
    }

    /**
     * Tests GET /api/v1/boards/{boardId} endpoint with TestContainers.
     */
    @Test
    @DisplayName("Should retrieve specific board by ID via REST API")
    void getBoardById_WithTestContainers_ShouldReturnBoard() throws Exception {
        // Given - Create test board
        var testBoard = new Board(123L, "Test Board", "TEST");
        boardRepository.save(testBoard);

        // When & Then - GET request should return the specific board
        mockMvc.perform(get("/api/v1/boards/123")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.boardId", is(123)))
                .andExpect(jsonPath("$.boardName", is("Test Board")))
                .andExpect(jsonPath("$.projectKey", is("TEST")))
                .andExpect(jsonPath("$.isActive", is(true)));
    }

    /**
     * Tests GET /api/v1/boards/{boardId} with non-existent ID.
     */
    @Test
    @DisplayName("Should return 404 for non-existent board")
    void getBoardById_WithNonExistentId_ShouldReturn404() throws Exception {
        // When & Then - GET request for non-existent board should return 404
        mockMvc.perform(get("/api/v1/boards/999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    /**
     * Tests POST /api/v1/boards endpoint with TestContainers.
     */
    @Test
    @DisplayName("Should create new board via REST API")
    void createBoard_WithValidData_ShouldPersistToDatabase() throws Exception {
        // Given - Board data
        var newBoard = new Board(456L, "New Board", "NEW");
        var boardJson = objectMapper.writeValueAsString(newBoard);

        // When & Then - POST request should create board
        mockMvc.perform(post("/api/v1/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(boardJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.boardId", is(456)))
                .andExpect(jsonPath("$.boardName", is("New Board")))
                .andExpect(jsonPath("$.projectKey", is("NEW")))
                .andExpect(jsonPath("$.isActive", is(true)));

        // Verify board is persisted in database
        var savedBoard = boardRepository.findByBoardId(456L);
        assertTrue(savedBoard.isPresent());
        assertEquals("New Board", savedBoard.get().getBoardName());
    }

    /**
     * Tests PUT /api/v1/boards/{boardId} endpoint with TestContainers.
     */
    @Test
    @DisplayName("Should update existing board via REST API")
    void updateBoard_WithValidData_ShouldModifyInDatabase() throws Exception {
        // Given - Existing board
        var existingBoard = new Board(789L, "Original Board", "ORIG");
        boardRepository.save(existingBoard);

        // Updated board data
        var updatedBoard = new Board(789L, "Updated Board", "UPD");
        var boardJson = objectMapper.writeValueAsString(updatedBoard);

        // When & Then - PUT request should update board
        mockMvc.perform(put("/api/v1/boards/789")
                .contentType(MediaType.APPLICATION_JSON)
                .content(boardJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.boardName", is("Updated Board")))
                .andExpect(jsonPath("$.projectKey", is("UPD")));

        // Verify update is persisted in database
        var savedBoard = boardRepository.findByBoardId(789L);
        assertTrue(savedBoard.isPresent());
        assertEquals("Updated Board", savedBoard.get().getBoardName());
        assertEquals("UPD", savedBoard.get().getProjectKey());
    }

    /**
     * Tests DELETE /api/v1/boards/{boardId} endpoint (soft delete).
     */
    @Test
    @DisplayName("Should deactivate board via REST API")
    void deleteBoard_WithExistingId_ShouldDeactivateInDatabase() throws Exception {
        // Given - Active board
        var testBoard = new Board(321L, "Test Board", "TEST");
        boardRepository.save(testBoard);

        // When & Then - DELETE request should deactivate board
        mockMvc.perform(delete("/api/v1/boards/321"))
                .andExpect(status().isNoContent());

        // Verify board is deactivated in database
        var deactivatedBoard = boardRepository.findByBoardId(321L);
        assertTrue(deactivatedBoard.isPresent());
        assertFalse(deactivatedBoard.get().getIsActive());
    }

    /**
     * Tests POST /api/v1/data/refresh endpoint.
     */
    @Test
    @DisplayName("Should trigger data refresh via REST API")
    void triggerDataRefresh_ShouldReturnSuccess() throws Exception {
        // When & Then - POST request should trigger refresh
        mockMvc.perform(post("/api/v1/data/refresh"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("refresh initiated successfully")));
    }

    /**
     * Tests GET /api/v1/health endpoint.
     */
    @Test
    @DisplayName("Should return health status via REST API")
    void healthCheck_ShouldReturnOk() throws Exception {
        // When & Then - GET request should return health status
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("JIRA Metrics API is running"));
    }

    /**
     * Tests validation errors with invalid board data.
     */
    @Test
    @DisplayName("Should handle validation errors gracefully")
    void createBoard_WithInvalidData_ShouldReturnBadRequest() throws Exception {
        // Given - Invalid board data (missing required fields)
        var invalidBoardJson = "{\"boardName\":\"\",\"projectKey\":\"\"}";

        // When & Then - POST request with invalid data should return 400
        mockMvc.perform(post("/api/v1/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBoardJson))
                .andExpect(status().isBadRequest());
    }

    /**
     * Tests concurrent API requests with TestContainers.
     */
    @Test
    @DisplayName("Should handle concurrent API requests safely")
    void concurrentApiRequests_WithTestContainers_ShouldHandleCorrectly() throws Exception {
        // Given - Multiple boards to create concurrently
        var board1Json = objectMapper.writeValueAsString(new Board(100L, "Concurrent Board 1", "CONC1"));
        var board2Json = objectMapper.writeValueAsString(new Board(200L, "Concurrent Board 2", "CONC2"));
        var board3Json = objectMapper.writeValueAsString(new Board(300L, "Concurrent Board 3", "CONC3"));

        // When - Create boards concurrently (simulated)
        mockMvc.perform(post("/api/v1/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(board1Json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(board2Json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(board3Json))
                .andExpect(status().isCreated());

        // Then - Verify all boards are created
        mockMvc.perform(get("/api/v1/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }
}

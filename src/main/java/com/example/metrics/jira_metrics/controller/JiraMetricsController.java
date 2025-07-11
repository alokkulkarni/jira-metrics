package com.example.metrics.jira_metrics.controller;

import com.example.metrics.jira_metrics.entity.Board;
import com.example.metrics.jira_metrics.entity.BoardMetrics;
import com.example.metrics.jira_metrics.entity.JiraData;
import com.example.metrics.jira_metrics.entity.Team;
import com.example.metrics.jira_metrics.repository.BoardRepository;
import com.example.metrics.jira_metrics.repository.TeamRepository;
import com.example.metrics.jira_metrics.service.JiraClientService;
import com.example.metrics.jira_metrics.service.JiraDataScheduledJob;
import com.example.metrics.jira_metrics.service.JiraDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for managing JIRA boards and viewing collected data.
 * Provides endpoints for board management and data retrieval operations.
 */
@RestController
@RequestMapping("/api/v1")
public class JiraMetricsController {

    private static final Logger logger = LoggerFactory.getLogger(JiraMetricsController.class);

    private final BoardRepository boardRepository;
    private final TeamRepository teamRepository;
    private final JiraDataService jiraDataService;
    private final JiraDataScheduledJob jiraDataScheduledJob;
    private final JiraClientService jiraClientService;

    /**
     * Constructor for JiraMetricsController.
     *
     * @param boardRepository        Board repository
     * @param teamRepository         Team repository
     * @param jiraDataService        JIRA data service
     * @param jiraDataScheduledJob   Scheduled job service
     * @param jiraClientService      JIRA client service
     */
    public JiraMetricsController(BoardRepository boardRepository,
                                TeamRepository teamRepository,
                                JiraDataService jiraDataService,
                                JiraDataScheduledJob jiraDataScheduledJob,
                                JiraClientService jiraClientService) {
        this.boardRepository = boardRepository;
        this.teamRepository = teamRepository;
        this.jiraDataService = jiraDataService;
        this.jiraDataScheduledJob = jiraDataScheduledJob;
        this.jiraClientService = jiraClientService;
    }

    /**
     * Retrieves all active boards.
     *
     * @return List of active boards
     */
    @GetMapping("/boards")
    public ResponseEntity<List<Board>> getAllActiveBoards() {
        logger.debug("Retrieving all active boards");
        List<Board> boards = boardRepository.findAllActiveBoards();
        logger.info("Retrieved {} active boards", boards.size());
        return ResponseEntity.ok(boards);
    }

    /**
     * Retrieves a specific board by ID.
     *
     * @param boardId The board ID
     * @return Board entity if found
     */
    @GetMapping("/boards/{boardId}")
    public ResponseEntity<Board> getBoardById(@PathVariable Long boardId) {
        logger.debug("Retrieving board with ID: {}", boardId);

        Optional<Board> board = boardRepository.findByBoardId(boardId);
        if (board.isPresent()) {
            logger.debug("Found board: {}", board.get().getBoardName());
            return ResponseEntity.ok(board.get());
        } else {
            logger.warn("Board not found with ID: {}", boardId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Creates a new board configuration.
     *
     * @param board The board to create
     * @return Created board entity
     */
    @PostMapping("/boards")
    public ResponseEntity<Board> createBoard(@Valid @RequestBody Board board) {
        logger.info("Creating new board: {} with ID: {}", board.getBoardName(), board.getBoardId());

        try {
            Board savedBoard = boardRepository.save(board);
            logger.info("Successfully created board: {}", savedBoard.getBoardName());
            return ResponseEntity.status(HttpStatus.CREATED).body(savedBoard);
        } catch (Exception e) {
            logger.error("Error creating board: {}", board.getBoardName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Updates an existing board configuration.
     *
     * @param boardId The board ID
     * @param board   Updated board data
     * @return Updated board entity
     */
    @PutMapping("/boards/{boardId}")
    public ResponseEntity<Board> updateBoard(@PathVariable Long boardId,
                                           @Valid @RequestBody Board board) {
        logger.info("Updating board with ID: {}", boardId);

        Optional<Board> existingBoard = boardRepository.findByBoardId(boardId);
        if (existingBoard.isEmpty()) {
            logger.warn("Board not found for update with ID: {}", boardId);
            return ResponseEntity.notFound().build();
        }

        try {
            Board boardToUpdate = existingBoard.get();
            boardToUpdate.setBoardName(board.getBoardName());
            boardToUpdate.setProjectKey(board.getProjectKey());
            boardToUpdate.setIsActive(board.getIsActive());

            Board updatedBoard = boardRepository.save(boardToUpdate);
            logger.info("Successfully updated board: {}", updatedBoard.getBoardName());
            return ResponseEntity.ok(updatedBoard);
        } catch (Exception e) {
            logger.error("Error updating board with ID: {}", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deactivates a board (soft delete).
     *
     * @param boardId The board ID
     * @return No content response
     */
    @DeleteMapping("/boards/{boardId}")
    public ResponseEntity<Void> deactivateBoard(@PathVariable Long boardId) {
        logger.info("Deactivating board with ID: {}", boardId);

        Optional<Board> board = boardRepository.findByBoardId(boardId);
        if (board.isEmpty()) {
            logger.warn("Board not found for deactivation with ID: {}", boardId);
            return ResponseEntity.notFound().build();
        }

        try {
            Board boardToDeactivate = board.get();
            boardToDeactivate.setIsActive(false);
            boardRepository.save(boardToDeactivate);

            logger.info("Successfully deactivated board: {}", boardToDeactivate.getBoardName());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deactivating board with ID: {}", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves all active teams.
     *
     * @return List of active teams
     */
    @GetMapping("/teams")
    public ResponseEntity<List<Team>> getAllActiveTeams() {
        logger.debug("Retrieving all active teams");
        List<Team> teams = teamRepository.findAllActiveTeams();
        logger.info("Retrieved {} active teams", teams.size());
        return ResponseEntity.ok(teams);
    }

    /**
     * Retrieves JIRA data for a specific board.
     *
     * @param boardId The board ID
     * @return List of JIRA data records
     */
    @GetMapping("/boards/{boardId}/data")
    public ResponseEntity<List<JiraData>> getBoardData(@PathVariable Long boardId) {
        logger.debug("Retrieving JIRA data for board ID: {}", boardId);

        List<JiraData> jiraData = jiraDataService.getAllBoardData(boardId);
        logger.info("Retrieved {} JIRA data records for board ID: {}", jiraData.size(), boardId);
        return ResponseEntity.ok(jiraData);
    }

    /**
     * Retrieves the latest JIRA data for a specific board and data type.
     *
     * @param boardId  The board ID
     * @param dataType The data type (issues, sprints, board_config)
     * @return Latest JIRA data record
     */
    @GetMapping("/boards/{boardId}/data/latest")
    public ResponseEntity<JiraData> getLatestBoardData(@PathVariable Long boardId,
                                                      @RequestParam String dataType) {
        logger.debug("Retrieving latest {} data for board ID: {}", dataType, boardId);

        Optional<JiraData> latestData = jiraDataService.getLatestBoardData(boardId, dataType);
        if (latestData.isPresent()) {
            logger.debug("Found latest {} data for board ID: {}", dataType, boardId);
            return ResponseEntity.ok(latestData.get());
        } else {
            logger.warn("No {} data found for board ID: {}", dataType, boardId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Manually triggers JIRA data retrieval for all boards.
     * This endpoint allows manual execution of the scheduled job.
     *
     * @return Success message
     */
    @PostMapping("/data/refresh")
    public ResponseEntity<String> triggerDataRefresh() {
        logger.info("Manual JIRA data refresh triggered via API");

        try {
            jiraDataScheduledJob.triggerManualDataRetrieval();
            String message = "JIRA data refresh initiated successfully";
            logger.info(message);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            logger.error("Error triggering manual data refresh", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error triggering data refresh: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint to verify API availability.
     *
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("JIRA Metrics API is running");
    }

    /**
     * Tests JIRA connection and authentication.
     *
     * @return ResponseEntity with connection status
     */
    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testJiraConnection() {
        logger.info("Testing JIRA connection via API endpoint");

        Map<String, Object> response = new HashMap<>();
        boolean isConnected = jiraClientService.testConnection();

        response.put("connected", isConnected);
        response.put("timestamp", System.currentTimeMillis());

        if (isConnected) {
            response.put("message", "JIRA connection successful!");
            response.put("status", "SUCCESS");
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "JIRA connection failed. Check logs for details.");
            response.put("status", "FAILED");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * Manually triggers metrics calculation for a specific board.
     * This endpoint allows testing the metrics calculation process.
     *
     * @param boardId The board ID to calculate metrics for
     * @return ResponseEntity with calculation status
     */
    @PostMapping("/boards/{boardId}/calculate-metrics")
    public ResponseEntity<Map<String, Object>> calculateBoardMetrics(@PathVariable Long boardId) {
        logger.info("Manual metrics calculation triggered for board ID: {}", boardId);

        Map<String, Object> response = new HashMap<>();

        try {
            // Check if board exists
            Optional<Board> board = boardRepository.findByBoardId(boardId);
            if (board.isEmpty()) {
                response.put("status", "ERROR");
                response.put("message", "Board not found with ID: " + boardId);
                return ResponseEntity.notFound().build();
            }

            // Trigger metrics calculation through the data service
            jiraDataService.calculateBoardMetrics(boardId);

            response.put("status", "SUCCESS");
            response.put("message", "Metrics calculation completed for board " + boardId);
            response.put("boardId", boardId);
            response.put("boardName", board.get().getBoardName());
            response.put("timestamp", System.currentTimeMillis());

            logger.info("Successfully triggered metrics calculation for board: {}", boardId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error triggering metrics calculation for board {}: {}", boardId, e.getMessage(), e);
            response.put("status", "ERROR");
            response.put("message", "Error calculating metrics: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Retrieves calculated metrics for a specific board.
     *
     * @param boardId The board ID
     * @return List of calculated metrics
     */
    @GetMapping("/boards/{boardId}/metrics")
    public ResponseEntity<List<BoardMetrics>> getBoardMetrics(@PathVariable Long boardId) {
        logger.debug("Retrieving calculated metrics for board ID: {}", boardId);

        try {
            // This will need the BoardMetricsRepository to be injected
            // For now, return a placeholder response
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Metrics retrieval endpoint - implementation needed");
            response.put("boardId", boardId);

            // TODO: Implement actual metrics retrieval
            // List<BoardMetrics> metrics = boardMetricsRepository.findByBoardId(boardId);
            // return ResponseEntity.ok(metrics);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            logger.error("Error retrieving metrics for board {}: {}", boardId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Diagnostic endpoint to show all sprints and their metrics calculation status.
     * This helps debug why some sprints aren't getting metrics calculated.
     *
     * @param boardId The board ID to diagnose
     * @return Detailed sprint and metrics information
     */
    @GetMapping("/boards/{boardId}/diagnostics")
    public ResponseEntity<Map<String, Object>> getBoardDiagnostics(@PathVariable Long boardId) {
        logger.info("Running diagnostics for board ID: {}", boardId);

        try {
            Map<String, Object> diagnostics = new HashMap<>();

            // Check if board exists
            Optional<Board> board = boardRepository.findByBoardId(boardId);
            if (board.isEmpty()) {
                diagnostics.put("error", "Board not found with ID: " + boardId);
                return ResponseEntity.notFound().build();
            }

            diagnostics.put("boardInfo", Map.of(
                "boardId", board.get().getBoardId(),
                "boardName", board.get().getBoardName(),
                "projectKey", board.get().getProjectKey(),
                "isActive", board.get().getIsActive()
            ));

            // Get all sprints for this board
            var allSprints = jiraDataService.getAllSprintsForBoard(boardId);
            logger.info("Found {} total sprints for board {}", allSprints.size(), boardId);

            List<Map<String, Object>> sprintDetails = new ArrayList<>();
            int completedCount = 0;
            int activeCount = 0;
            int otherCount = 0;

            for (var sprint : allSprints) {
                Map<String, Object> sprintInfo = new HashMap<>();
                sprintInfo.put("sprintId", sprint.sprintId());
                sprintInfo.put("sprintName", sprint.sprintName());
                sprintInfo.put("sprintState", sprint.sprintState());
                sprintInfo.put("startDate", sprint.startDate());
                sprintInfo.put("endDate", sprint.endDate());
                sprintInfo.put("completeDate", sprint.completeDate());
                sprintInfo.put("isCompleted", sprint.isCompleted());

                // Check if metrics exist for this sprint
                boolean hasMetrics = jiraDataService.sprintHasMetrics(boardId, sprint.sprintId());
                sprintInfo.put("hasMetrics", hasMetrics);

                sprintDetails.add(sprintInfo);

                // Count sprint types
                if (sprint.isCompleted()) {
                    completedCount++;
                } else if ("ACTIVE".equalsIgnoreCase(sprint.sprintState())) {
                    activeCount++;
                } else {
                    otherCount++;
                }
            }

            diagnostics.put("sprintSummary", Map.of(
                "totalSprints", allSprints.size(),
                "completedSprints", completedCount,
                "activeSprints", activeCount,
                "otherSprints", otherCount
            ));

            diagnostics.put("sprints", sprintDetails);
            diagnostics.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(diagnostics);

        } catch (Exception e) {
            logger.error("Error running diagnostics for board {}: {}", boardId, e.getMessage(), e);
            Map<String, Object> error = Map.of(
                "error", "Error running diagnostics: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}

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
import com.example.metrics.jira_metrics.service.BoardSynchronizationService;
import com.example.metrics.jira_metrics.service.BoardValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for managing JIRA boards and viewing collected data.
 * Provides endpoints for board management, validation, and metrics retrieval operations.
 * Supports both sprint-based and issue-based metrics calculation.
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
    private final BoardSynchronizationService boardSynchronizationService;
    private final BoardValidationService boardValidationService;

    /**
     * Constructor for JiraMetricsController.
     *
     * @param boardRepository        Board repository
     * @param teamRepository         Team repository
     * @param jiraDataService        JIRA data service
     * @param jiraDataScheduledJob   Scheduled job service
     * @param jiraClientService      JIRA client service
     * @param boardSynchronizationService Board synchronization service
     * @param boardValidationService Board validation service
     */
    public JiraMetricsController(BoardRepository boardRepository,
                                TeamRepository teamRepository,
                                JiraDataService jiraDataService,
                                JiraDataScheduledJob jiraDataScheduledJob,
                                JiraClientService jiraClientService,
                                BoardSynchronizationService boardSynchronizationService,
                                BoardValidationService boardValidationService) {
        this.boardRepository = boardRepository;
        this.teamRepository = teamRepository;
        this.jiraDataService = jiraDataService;
        this.jiraDataScheduledJob = jiraDataScheduledJob;
        this.jiraClientService = jiraClientService;
        this.boardSynchronizationService = boardSynchronizationService;
        this.boardValidationService = boardValidationService;
    }

    /**
     * Retrieves all active boards with sprint validation information.
     *
     * @return List of active boards with sprint availability details
     */
    @GetMapping("/boards")
    public ResponseEntity<List<Map<String, Object>>> getAllActiveBoards() {
        logger.debug("Retrieving all active boards with validation");
        List<Board> boards = boardRepository.findAllActiveBoards();

        List<Map<String, Object>> boardsWithValidation = new ArrayList<>();
        for (Board board : boards) {
            Map<String, Object> boardInfo = new HashMap<>();
            boardInfo.put("board", board);
            boardInfo.put("boardType", board.getBoardType());
            boardInfo.put("hasSprints", board.getHasSprints());
            boardInfo.put("sprintCount", board.getSprintCount());
            boardInfo.put("supportsSprintMetrics", board.supportsSprintMetrics());
            boardInfo.put("metricsType", board.supportsSprintMetrics() ? "SPRINT_BASED" : "ISSUE_BASED");
            boardsWithValidation.add(boardInfo);
        }

        logger.info("Retrieved {} active boards with validation info", boards.size());
        return ResponseEntity.ok(boardsWithValidation);
    }

    /**
     * Validates a specific board and returns its sprint availability information.
     *
     * @param boardId The board ID to validate
     * @return Board validation information
     */
    @PostMapping("/boards/{boardId}/validate")
    public ResponseEntity<Map<String, Object>> validateBoard(@PathVariable Long boardId) {
        logger.info("Validating board ID: {}", boardId);

        Optional<Board> validatedBoard = boardValidationService.validateAndUpdateBoard(boardId);
        if (validatedBoard.isEmpty()) {
            logger.warn("Board not found or validation failed for ID: {}", boardId);
            return ResponseEntity.notFound().build();
        }

        Board board = validatedBoard.get();
        Map<String, Object> validationResult = new HashMap<>();
        validationResult.put("boardId", board.getBoardId());
        validationResult.put("boardName", board.getBoardName());
        validationResult.put("boardType", board.getBoardType());
        validationResult.put("hasSprints", board.getHasSprints());
        validationResult.put("sprintCount", board.getSprintCount());
        validationResult.put("supportsSprintMetrics", board.supportsSprintMetrics());
        validationResult.put("metricsType", board.supportsSprintMetrics() ? "SPRINT_BASED" : "ISSUE_BASED");
        validationResult.put("lastValidated", board.getUpdatedAt());

        logger.info("Board {} validated - Type: {}, Has Sprints: {}, Metrics Type: {}",
                   boardId, board.getBoardType(), board.getHasSprints(),
                   board.supportsSprintMetrics() ? "SPRINT_BASED" : "ISSUE_BASED");

        return ResponseEntity.ok(validationResult);
    }

    /**
     * Calculates and retrieves metrics for a board, automatically determining
     * whether to use sprint-based or issue-based calculation.
     *
     * @param boardId The board ID
     * @param periodStart Optional start date for metrics calculation
     * @param periodEnd Optional end date for metrics calculation
     * @return Calculated metrics with type information
     */
    @GetMapping("/boards/{boardId}/metrics")
    public ResponseEntity<Map<String, Object>> getBoardMetrics(
            @PathVariable Long boardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime periodEnd) {

        logger.info("Calculating metrics for board ID: {} with period {} to {}",
                   boardId, periodStart, periodEnd);

        Optional<BoardMetrics> metricsOpt = boardValidationService.calculateBoardMetrics(
            boardId, periodStart, periodEnd);

        if (metricsOpt.isEmpty()) {
            logger.warn("Unable to calculate metrics for board ID: {}", boardId);
            return ResponseEntity.notFound().build();
        }

        BoardMetrics metrics = metricsOpt.get();

        Map<String, Object> response = new HashMap<>();
        response.put("metrics", metrics);
        response.put("metricType", metrics.metricType());
        response.put("boardType", metrics.boardType());
        response.put("isSprintBased", metrics.isSprintBased());
        response.put("isIssueBased", metrics.isIssueBased());
        response.put("calculationPeriod", Map.of(
            "start", metrics.metricPeriodStart(),
            "end", metrics.metricPeriodEnd()
        ));

        // Add contextual information based on metric type
        if (metrics.isSprintBased()) {
            response.put("sprintId", metrics.sprintId());
            response.put("description", "Metrics calculated based on sprint data and sprint completion");
        } else {
            response.put("description", "Metrics calculated based on issue status transitions and backlog analysis");
            response.put("issueBreakdown", Map.of(
                "inProgress", metrics.issuesInProgress(),
                "inBacklog", metrics.issuesInBacklog(),
                "done", metrics.issuesDone()
            ));
        }

        logger.info("Successfully calculated {} metrics for board {}",
                   metrics.metricType(), boardId);

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a specific board by ID with validation information.
     *
     * @param boardId The board ID
     * @return Board entity with validation details if found
     */
    @GetMapping("/boards/{boardId}")
    public ResponseEntity<Map<String, Object>> getBoardById(@PathVariable Long boardId) {
        logger.debug("Retrieving board with ID: {}", boardId);

        Optional<Board> boardOpt = boardRepository.findByBoardId(boardId);
        if (boardOpt.isEmpty()) {
            logger.warn("Board not found with ID: {}", boardId);
            return ResponseEntity.notFound().build();
        }

        Board board = boardOpt.get();
        Map<String, Object> response = new HashMap<>();
        response.put("board", board);
        response.put("boardType", board.getBoardType());
        response.put("hasSprints", board.getHasSprints());
        response.put("sprintCount", board.getSprintCount());
        response.put("supportsSprintMetrics", board.supportsSprintMetrics());
        response.put("recommendedMetricsType", board.supportsSprintMetrics() ? "SPRINT_BASED" : "ISSUE_BASED");

        logger.debug("Found board: {} (Type: {}, Has Sprints: {})",
                    board.getBoardName(), board.getBoardType(), board.getHasSprints());

        return ResponseEntity.ok(response);
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

    /**
     * Manually triggers board synchronization from JIRA.
     * This endpoint allows immediate synchronization of all boards.
     *
     * @return ResponseEntity with synchronization status
     */
    @PostMapping("/boards/synchronize")
    public ResponseEntity<Map<String, Object>> synchronizeBoards() {
        logger.info("Manual board synchronization triggered via API");

        Map<String, Object> response = new HashMap<>();

        try {
            int synchronizedCount = boardSynchronizationService.synchronizeBoardsManually();

            response.put("status", "SUCCESS");
            response.put("message", "Board synchronization completed successfully");
            response.put("boardsProcessed", synchronizedCount);
            response.put("timestamp", System.currentTimeMillis());

            logger.info("Successfully synchronized {} boards", synchronizedCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error during manual board synchronization: {}", e.getMessage(), e);
            response.put("status", "ERROR");
            response.put("message", "Error synchronizing boards: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Gets board synchronization status and statistics.
     *
     * @return Board synchronization information
     */
    @GetMapping("/boards/sync-status")
    public ResponseEntity<Map<String, Object>> getBoardSyncStatus() {
        logger.debug("Retrieving board synchronization status");

        try {
            Map<String, Object> status = new HashMap<>();

            long activeBoardCount = boardSynchronizationService.getActiveBoardCount();
            var lastSyncTime = boardSynchronizationService.getLastSynchronizationTime();

            status.put("activeBoardCount", activeBoardCount);
            status.put("lastSynchronizationTime", lastSyncTime);
            status.put("nextScheduledSync", "Every 2 hours");
            status.put("status", "OPERATIONAL");
            status.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("Error retrieving board sync status: {}", e.getMessage(), e);
            Map<String, Object> error = Map.of(
                "status", "ERROR",
                "message", "Error retrieving sync status: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}

package com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.entity.Board;
import com.example.metrics.jira_metrics.repository.BoardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrator service that manages the complete JIRA data synchronization process.
 * Ensures proper order: Boards → Sprints → Issues → Metrics
 * Handles both sprint-based boards (Scrum) and non-sprint boards (Kanban).
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Service
@Transactional
public class DataSynchronizationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(DataSynchronizationOrchestrator.class);

    private final BoardSynchronizationService boardSynchronizationService;
    private final SprintSynchronizationService sprintSynchronizationService;
    private final IssueSynchronizationService issueSynchronizationService;
    private final BoardRepository boardRepository;

    /**
     * Constructor for DataSynchronizationOrchestrator.
     *
     * @param boardSynchronizationService Service for board synchronization
     * @param sprintSynchronizationService Service for sprint synchronization
     * @param issueSynchronizationService Service for issue synchronization
     * @param boardRepository Repository for board operations
     */
    public DataSynchronizationOrchestrator(
            BoardSynchronizationService boardSynchronizationService,
            SprintSynchronizationService sprintSynchronizationService,
            IssueSynchronizationService issueSynchronizationService,
            BoardRepository boardRepository) {
        this.boardSynchronizationService = boardSynchronizationService;
        this.sprintSynchronizationService = sprintSynchronizationService;
        this.issueSynchronizationService = issueSynchronizationService;
        this.boardRepository = boardRepository;
    }

    /**
     * Initial full synchronization at application startup.
     * Runs 60 seconds after startup to allow application to fully initialize.
     */
    @Scheduled(initialDelay = 60000) // Wait 60 seconds after startup
    public void initialFullSynchronization() {
        logger.info("=== Starting Initial Full JIRA Data Synchronization ===");
        performFullSynchronization();
        logger.info("=== Initial Full JIRA Data Synchronization Completed ===");
    }

    /**
     * Scheduled full synchronization every 4 hours.
     * Ensures data freshness while not overwhelming the JIRA API.
     */
    @Scheduled(fixedRate = 4, timeUnit = TimeUnit.HOURS)
    public void scheduledFullSynchronization() {
        logger.info("=== Starting Scheduled Full JIRA Data Synchronization ===");
        performFullSynchronization();
        logger.info("=== Scheduled Full JIRA Data Synchronization Completed ===");
    }

    /**
     * Manually triggered full synchronization.
     * Can be called via API endpoints for immediate data refresh.
     *
     * @return Synchronization summary
     */
    public SynchronizationSummary synchronizeAllDataManually() {
        logger.info("=== Starting Manual Full JIRA Data Synchronization ===");
        SynchronizationSummary summary = performFullSynchronization();
        logger.info("=== Manual Full JIRA Data Synchronization Completed ===");
        return summary;
    }

    /**
     * Performs the complete data synchronization in the correct order.
     * Order: Boards → Sprints (for sprint-enabled boards) → Issues → Metrics
     *
     * @return Synchronization summary with counts
     */
    private SynchronizationSummary performFullSynchronization() {
        SynchronizationSummary summary = new SynchronizationSummary();

        try {
            // Step 1: Synchronize Boards
            logger.info("Step 1/3: Synchronizing boards...");
            int boardCount = boardSynchronizationService.synchronizeBoardsManually();
            summary.setBoardCount(boardCount);
            logger.info("Synchronized {} boards", boardCount);

            if (boardCount == 0) {
                logger.warn("No boards synchronized. Skipping subsequent steps.");
                return summary;
            }

            // Step 2: Synchronize Sprints (for boards that support sprints)
            logger.info("Step 2/3: Synchronizing sprints for sprint-enabled boards...");
            int totalSprintCount = synchronizeSprintsForAllBoards();
            summary.setSprintCount(totalSprintCount);
            logger.info("Synchronized {} sprints across all boards", totalSprintCount);

            // Step 3: Synchronize Issues (with proper board/sprint linking)
            logger.info("Step 3/3: Synchronizing issues for all boards...");
            int totalIssueCount = synchronizeIssuesForAllBoards();
            summary.setIssueCount(totalIssueCount);
            logger.info("Synchronized {} issues across all boards", totalIssueCount);

            summary.setSuccess(true);
            summary.setMessage("Full synchronization completed successfully");

        } catch (Exception e) {
            logger.error("Error during full synchronization: {}", e.getMessage(), e);
            summary.setSuccess(false);
            summary.setMessage("Full synchronization failed: " + e.getMessage());
        }

        return summary;
    }

    /**
     * Synchronizes sprints for all boards that have sprints enabled.
     *
     * @return Total number of sprints synchronized
     */
    private int synchronizeSprintsForAllBoards() {
        List<Board> allBoards = boardRepository.findByIsActiveTrue();
        int totalSprintCount = 0;

        for (Board board : allBoards) {
            try {
                if (board.getHasSprints() != null && board.getHasSprints()) {
                    logger.debug("Synchronizing sprints for board: {} (ID: {})",
                               board.getBoardName(), board.getBoardId());

                    int sprintCount = sprintSynchronizationService.synchronizeSprintsForBoard(
                        board.getBoardId(),
                        board.getHasSprints()
                    );

                    totalSprintCount += sprintCount;

                    // Update board sprint count
                    board.setSprintCount(sprintCount);
                    boardRepository.save(board);

                } else {
                    logger.debug("Board {} does not have sprints enabled, skipping", board.getBoardName());
                }
            } catch (Exception e) {
                logger.error("Error synchronizing sprints for board {}: {}",
                           board.getBoardName(), e.getMessage());
            }
        }

        return totalSprintCount;
    }

    /**
     * Synchronizes issues for all boards with proper sprint/board linking.
     *
     * @return Total number of issues synchronized
     */
    private int synchronizeIssuesForAllBoards() {
        List<Board> allBoards = boardRepository.findByIsActiveTrue();
        int totalIssueCount = 0;

        for (Board board : allBoards) {
            try {
                boolean hasSprints = board.getHasSprints() != null && board.getHasSprints();

                logger.debug("Synchronizing issues for board: {} (ID: {}, Type: {}, Has Sprints: {})",
                           board.getBoardName(), board.getBoardId(),
                           board.getBoardType(), hasSprints);

                int issueCount = issueSynchronizationService.synchronizeIssuesForBoard(
                    board.getBoardId(),
                    hasSprints,
                    board.getBoardType()
                );

                totalIssueCount += issueCount;

                logger.info("Synchronized {} issues for board: {}", issueCount, board.getBoardName());

            } catch (Exception e) {
                logger.error("Error synchronizing issues for board {}: {}",
                           board.getBoardName(), e.getMessage());
            }
        }

        return totalIssueCount;
    }

    /**
     * Synchronizes data for a specific board only.
     * Useful for targeted synchronization when needed.
     *
     * @param boardId The board ID to synchronize
     * @return Board-specific synchronization summary
     */
    public BoardSynchronizationSummary synchronizeBoardData(Long boardId) {
        logger.info("Starting targeted synchronization for board ID: {}", boardId);

        BoardSynchronizationSummary summary = new BoardSynchronizationSummary();
        summary.setBoardId(boardId);

        try {
            Board board = boardRepository.findByBoardId(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + boardId));

            // Synchronize sprints if board supports them
            int sprintCount = 0;
            boolean hasSprints = board.getHasSprints() != null && board.getHasSprints();
            if (hasSprints) {
                sprintCount = sprintSynchronizationService.synchronizeSprintsForBoard(
                    boardId, true);
                board.setSprintCount(sprintCount);
                boardRepository.save(board);
            }

            // Synchronize issues
            int issueCount = issueSynchronizationService.synchronizeIssuesForBoard(
                boardId,
                hasSprints,
                board.getBoardType()
            );

            summary.setSprintCount(sprintCount);
            summary.setIssueCount(issueCount);
            summary.setSuccess(true);
            summary.setMessage("Board synchronization completed successfully");

            logger.info("Completed targeted synchronization for board {}: {} sprints, {} issues",
                       board.getBoardName(), sprintCount, issueCount);

        } catch (Exception e) {
            logger.error("Error during targeted board synchronization for board ID {}: {}",
                        boardId, e.getMessage(), e);
            summary.setSuccess(false);
            summary.setMessage("Board synchronization failed: " + e.getMessage());
        }

        return summary;
    }

    /**
     * Summary class for overall synchronization results.
     */
    public static class SynchronizationSummary {
        private boolean success;
        private String message;
        private int boardCount;
        private int sprintCount;
        private int issueCount;

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public int getBoardCount() { return boardCount; }
        public void setBoardCount(int boardCount) { this.boardCount = boardCount; }

        public int getSprintCount() { return sprintCount; }
        public void setSprintCount(int sprintCount) { this.sprintCount = sprintCount; }

        public int getIssueCount() { return issueCount; }
        public void setIssueCount(int issueCount) { this.issueCount = issueCount; }

        @Override
        public String toString() {
            return String.format("SynchronizationSummary{success=%s, message='%s', boards=%d, sprints=%d, issues=%d}",
                    success, message, boardCount, sprintCount, issueCount);
        }
    }

    /**
     * Summary class for board-specific synchronization results.
     */
    public static class BoardSynchronizationSummary {
        private Long boardId;
        private boolean success;
        private String message;
        private int sprintCount;
        private int issueCount;

        // Getters and setters
        public Long getBoardId() { return boardId; }
        public void setBoardId(Long boardId) { this.boardId = boardId; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public int getSprintCount() { return sprintCount; }
        public void setSprintCount(int sprintCount) { this.sprintCount = sprintCount; }

        public int getIssueCount() { return issueCount; }
        public void setIssueCount(int issueCount) { this.issueCount = issueCount; }

        @Override
        public String toString() {
            return String.format("BoardSynchronizationSummary{boardId=%d, success=%s, message='%s', sprints=%d, issues=%d}",
                    boardId, success, message, sprintCount, issueCount);
        }
    }
}

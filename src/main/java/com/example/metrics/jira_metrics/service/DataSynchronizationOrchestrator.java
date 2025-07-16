package com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.entity.Board;
import com.example.metrics.jira_metrics.entity.Sprint;
import com.example.metrics.jira_metrics.entity.Issue;
import com.example.metrics.jira_metrics.repository.BoardRepository;
import com.example.metrics.jira_metrics.repository.SprintRepository;
import com.example.metrics.jira_metrics.repository.IssueRepository;
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
    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;

    /**
     * Constructor for DataSynchronizationOrchestrator.
     *
     * @param boardSynchronizationService Service for board synchronization
     * @param sprintSynchronizationService Service for sprint synchronization
     * @param issueSynchronizationService Service for issue synchronization
     * @param boardRepository Repository for board operations
     * @param sprintRepository Repository for sprint operations
     * @param issueRepository Repository for issue operations
     */
    public DataSynchronizationOrchestrator(
            BoardSynchronizationService boardSynchronizationService,
            SprintSynchronizationService sprintSynchronizationService,
            IssueSynchronizationService issueSynchronizationService,
            BoardRepository boardRepository,
            SprintRepository sprintRepository,
            IssueRepository issueRepository) {
        this.boardSynchronizationService = boardSynchronizationService;
        this.sprintSynchronizationService = sprintSynchronizationService;
        this.issueSynchronizationService = issueSynchronizationService;
        this.boardRepository = boardRepository;
        this.sprintRepository = sprintRepository;
        this.issueRepository = issueRepository;
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
     * Synchronizes sprints for all active boards that have sprints enabled.
     * Only processes boards marked as is_active = true in the database.
     *
     * @return Total number of sprints synchronized
     */
    private int synchronizeSprintsForAllBoards() {
        List<Board> activeBoards = boardRepository.findByIsActiveTrue();
        logger.info("Found {} active boards for sprint synchronization", activeBoards.size());

        int totalSprintCount = 0;
        int boardsWithSprints = 0;
        int boardsSkipped = 0;

        for (Board board : activeBoards) {
            try {
                // Only process boards that are active AND have sprints enabled
                if (board.getHasSprints() != null && board.getHasSprints()) {
                    logger.debug("Synchronizing sprints for active board: {} (ID: {})",
                               board.getBoardName(), board.getBoardId());

                    int sprintCount = sprintSynchronizationService.synchronizeSprintsForBoard(
                        board.getBoardId(),
                        board.getHasSprints()
                    );

                    totalSprintCount += sprintCount;
                    boardsWithSprints++;

                    // Update board sprint count
                    board.setSprintCount(sprintCount);
                    boardRepository.save(board);

                } else {
                    logger.debug("Skipping sprint sync for board {} - no sprints enabled or board inactive",
                               board.getBoardName());
                    boardsSkipped++;
                }
            } catch (Exception e) {
                logger.error("Error synchronizing sprints for active board {}: {}",
                           board.getBoardName(), e.getMessage());
            }
        }

        // Clean up sprints for inactive boards
        cleanupSprintsForInactiveBoards();

        logger.info("Sprint synchronization completed: {} boards with sprints processed, {} boards skipped, {} total sprints synced",
                   boardsWithSprints, boardsSkipped, totalSprintCount);

        return totalSprintCount;
    }

    /**
     * Synchronizes issues for all active boards with proper sprint/board linking.
     * Only processes boards marked as is_active = true in the database.
     *
     * @return Total number of issues synchronized
     */
    private int synchronizeIssuesForAllBoards() {
        List<Board> activeBoards = boardRepository.findByIsActiveTrue();
        logger.info("Found {} active boards for issue synchronization", activeBoards.size());

        int totalIssueCount = 0;
        int boardsProcessed = 0;

        for (Board board : activeBoards) {
            try {
                boolean hasSprints = board.getHasSprints() != null && board.getHasSprints();

                logger.debug("Synchronizing issues for active board: {} (ID: {}, Type: {}, Has Sprints: {})",
                           board.getBoardName(), board.getBoardId(),
                           board.getBoardType(), hasSprints);

                int issueCount = issueSynchronizationService.synchronizeIssuesForBoard(
                    board.getBoardId(),
                    hasSprints,
                    board.getBoardType()
                );

                totalIssueCount += issueCount;
                boardsProcessed++;

                logger.info("Synchronized {} issues for active board: {}", issueCount, board.getBoardName());

            } catch (Exception e) {
                logger.error("Error synchronizing issues for active board {}: {}",
                           board.getBoardName(), e.getMessage());
            }
        }

        // Clean up issues for inactive boards
        cleanupIssuesForInactiveBoards();

        logger.info("Issue synchronization completed: {} active boards processed, {} total issues synced",
                   boardsProcessed, totalIssueCount);

        return totalIssueCount;
    }

    /**
     * Removes sprints associated with boards that are no longer active.
     * This ensures data consistency and prevents orphaned sprint data.
     */
    private void cleanupSprintsForInactiveBoards() {
        try {
            logger.debug("Cleaning up sprints for inactive boards...");

            List<Board> inactiveBoards = boardRepository.findByIsActiveFalse();
            int sprintsRemoved = 0;

            for (Board inactiveBoard : inactiveBoards) {
                try {
                    // Get sprints for this inactive board
                    List<Sprint> sprintsToRemove = sprintRepository.findByBoardId(inactiveBoard.getBoardId());

                    if (!sprintsToRemove.isEmpty()) {
                        sprintRepository.deleteByBoardId(inactiveBoard.getBoardId());
                        sprintsRemoved += sprintsToRemove.size();

                        logger.info("Removed {} sprints for inactive board: {} (ID: {})",
                                   sprintsToRemove.size(), inactiveBoard.getBoardName(), inactiveBoard.getBoardId());
                    }
                } catch (Exception e) {
                    logger.error("Error cleaning up sprints for inactive board {}: {}",
                               inactiveBoard.getBoardName(), e.getMessage());
                }
            }

            if (sprintsRemoved > 0) {
                logger.info("Sprint cleanup completed: removed {} sprints from {} inactive boards",
                           sprintsRemoved, inactiveBoards.size());
            } else {
                logger.debug("No sprint cleanup needed - no sprints found for inactive boards");
            }

        } catch (Exception e) {
            logger.error("Error during sprint cleanup for inactive boards: {}", e.getMessage(), e);
        }
    }

    /**
     * Removes issues associated with boards that are no longer active.
     * This ensures data consistency and prevents orphaned issue data.
     */
    private void cleanupIssuesForInactiveBoards() {
        try {
            logger.debug("Cleaning up issues for inactive boards...");

            List<Board> inactiveBoards = boardRepository.findByIsActiveFalse();
            int issuesRemoved = 0;

            for (Board inactiveBoard : inactiveBoards) {
                try {
                    // Get issues for this inactive board
                    List<Issue> issuesToRemove = issueRepository.findByBoardId(inactiveBoard.getBoardId());

                    if (!issuesToRemove.isEmpty()) {
                        issueRepository.deleteByBoardId(inactiveBoard.getBoardId());
                        issuesRemoved += issuesToRemove.size();

                        logger.info("Removed {} issues for inactive board: {} (ID: {})",
                                   issuesToRemove.size(), inactiveBoard.getBoardName(), inactiveBoard.getBoardId());
                    }
                } catch (Exception e) {
                    logger.error("Error cleaning up issues for inactive board {}: {}",
                               inactiveBoard.getBoardName(), e.getMessage());
                }
            }

            if (issuesRemoved > 0) {
                logger.info("Issue cleanup completed: removed {} issues from {} inactive boards",
                           issuesRemoved, inactiveBoards.size());
            } else {
                logger.debug("No issue cleanup needed - no issues found for inactive boards");
            }

        } catch (Exception e) {
            logger.error("Error during issue cleanup for inactive boards: {}", e.getMessage(), e);
        }
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

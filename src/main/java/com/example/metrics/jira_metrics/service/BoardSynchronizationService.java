package com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.entity.Board;
import com.example.metrics.jira_metrics.repository.BoardRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for synchronizing JIRA boards with the local database.
 * Fetches all boards from JIRA API and maintains them in the boards table.
 * Runs at application startup and refreshes every 2 hours.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Service
@Transactional
public class BoardSynchronizationService {

    private static final Logger logger = LoggerFactory.getLogger(BoardSynchronizationService.class);

    private final JiraClientService jiraClientService;
    private final BoardRepository boardRepository;

    /**
     * Constructor for BoardSynchronizationService.
     *
     * @param jiraClientService JIRA client service for API calls
     * @param boardRepository Repository for board operations
     */
    public BoardSynchronizationService(JiraClientService jiraClientService,
                                     BoardRepository boardRepository) {
        this.jiraClientService = jiraClientService;
        this.boardRepository = boardRepository;
    }

    /**
     * Synchronizes boards at application startup.
     * This method runs automatically when the application starts.
     */
    @Scheduled(initialDelay = 30000) // Wait 30 seconds after startup
    public void initializeBoardSynchronization() {
        logger.info("Starting initial board synchronization at application startup");
        synchronizeBoards();
    }

    /**
     * Scheduled method to synchronize boards every 2 hours.
     * Fetches all boards from JIRA and updates the local database.
     */
    @Scheduled(fixedRate = 2, timeUnit = TimeUnit.HOURS)
    public void scheduledBoardSynchronization() {
        logger.info("Starting scheduled board synchronization (every 2 hours)");
        synchronizeBoards();
    }

    /**
     * Manually triggers board synchronization.
     * This method can be called via API endpoints for immediate synchronization.
     *
     * @return Number of boards synchronized
     */
    public int synchronizeBoardsManually() {
        logger.info("Starting manual board synchronization");
        return synchronizeBoards();
    }

    /**
     * Core method that performs the board synchronization logic.
     * Fetches all boards from JIRA API and updates the local database.
     * Uses upsert strategy to handle existing boards gracefully.
     *
     * @return Number of boards processed
     */
    private int synchronizeBoards() {
        try {
            logger.info("Fetching all boards from JIRA API");

            Optional<JsonNode> boardsData = jiraClientService.getAllBoards();
            if (boardsData.isEmpty()) {
                logger.warn("No boards data retrieved from JIRA API");
                return 0;
            }

            JsonNode boardsJson = boardsData.get();
            JsonNode boardValues = boardsJson.path("values");

            if (boardValues.isMissingNode() || !boardValues.isArray()) {
                logger.warn("Invalid boards data structure from JIRA API");
                return 0;
            }

            Set<Long> activeBoardIds = new HashSet<>();
            int processedCount = 0;
            int newBoardsCount = 0;
            int updatedBoardsCount = 0;

            // Process each board from JIRA using upsert strategy
            for (JsonNode boardNode : boardValues) {
                try {
                    Board processedBoard = processBoardFromJson(boardNode);
                    if (processedBoard != null) {
                        activeBoardIds.add(processedBoard.getBoardId());

                        // Use upsert strategy to handle duplicates gracefully
                        UpsertResult result = upsertBoard(processedBoard);
                        if (result.isNewBoard()) {
                            newBoardsCount++;
                            logger.debug("Created new board: {} (ID: {})",
                                       processedBoard.getBoardName(), processedBoard.getBoardId());
                        } else {
                            updatedBoardsCount++;
                            logger.debug("Updated existing board: {} (ID: {})",
                                       processedBoard.getBoardName(), processedBoard.getBoardId());
                        }
                        processedCount++;
                    }
                } catch (Exception e) {
                    logger.error("Error processing individual board from JIRA: {}", e.getMessage(), e);
                    // Continue processing other boards even if one fails
                }
            }

            // Deactivate boards that are no longer in JIRA
            int deactivatedCount = deactivateRemovedBoards(activeBoardIds);

            logger.info("Board synchronization completed: {} total processed, {} new, {} updated, {} deactivated",
                       processedCount, newBoardsCount, updatedBoardsCount, deactivatedCount);

            return processedCount;

        } catch (Exception e) {
            logger.error("Error during board synchronization: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Performs upsert operation for a board (insert if new, update if exists).
     * This method handles duplicate key violations gracefully.
     *
     * @param board the board to upsert
     * @return UpsertResult indicating whether board was new or updated
     */
    private UpsertResult upsertBoard(Board board) {
        try {
            // First, try to find existing board
            Optional<Board> existingBoard = boardRepository.findByBoardId(board.getBoardId());

            if (existingBoard.isPresent()) {
                // Update existing board
                Board updated = updateExistingBoard(existingBoard.get(), board);
                boardRepository.save(updated);
                return new UpsertResult(false, updated);
            } else {
                // Try to insert new board
                try {
                    Board saved = boardRepository.save(board);
                    return new UpsertResult(true, saved);
                } catch (Exception e) {
                    // Handle race condition: another thread might have inserted the board
                    if (isDuplicateKeyException(e)) {
                        logger.debug("Duplicate key detected during insert, attempting update for board ID: {}",
                                   board.getBoardId());

                        // Try to find and update the board that was inserted by another thread
                        Optional<Board> raceConditionBoard = boardRepository.findByBoardId(board.getBoardId());
                        if (raceConditionBoard.isPresent()) {
                            Board updated = updateExistingBoard(raceConditionBoard.get(), board);
                            boardRepository.save(updated);
                            return new UpsertResult(false, updated);
                        } else {
                            throw new IllegalStateException("Board not found after duplicate key exception", e);
                        }
                    } else {
                        throw e; // Re-throw if it's not a duplicate key issue
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to upsert board ID {}: {}", board.getBoardId(), e.getMessage(), e);
            throw new RuntimeException("Board upsert failed", e);
        }
    }

    /**
     * Checks if an exception is caused by a duplicate key violation.
     *
     * @param exception the exception to check
     * @return true if it's a duplicate key exception
     */
    private boolean isDuplicateKeyException(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }

        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("duplicate key") ||
               lowerMessage.contains("unique constraint") ||
               lowerMessage.contains("boards_board_id_key") ||
               lowerMessage.contains("unique violation");
    }

    /**
     * Result of an upsert operation.
     *
     * @param isNewBoard true if the board was newly created, false if updated
     * @param board the resulting board entity
     */
    private record UpsertResult(boolean isNewBoard, Board board) {
        public boolean isNewBoard() {
            return isNewBoard;
        }
    }

    /**
     * Processes a single board JSON node from JIRA API into a Board entity.
     *
     * @param boardNode JSON node containing board data
     * @return Board entity or null if processing fails
     */
    private Board processBoardFromJson(JsonNode boardNode) {
        try {
            Long boardId = boardNode.path("id").asLong();
            String boardName = boardNode.path("name").asText();
            String boardType = boardNode.path("type").asText();

            if (boardId == 0 || boardName.isEmpty()) {
                logger.warn("Invalid board data - missing ID or name: {}", boardNode);
                return null;
            }

            // Extract project key from location
            String projectKey = null;
            JsonNode location = boardNode.path("location");
            if (!location.isMissingNode()) {
                projectKey = location.path("projectKey").asText();
                if (projectKey.isEmpty()) {
                    projectKey = location.path("key").asText(); // Alternative field name
                }
            }

            // If project key is still empty, try to extract from board name or use board type
            if (projectKey == null || projectKey.isEmpty()) {
                projectKey = extractProjectKeyFromName(boardName);
                if (projectKey == null || projectKey.isEmpty()) {
                    projectKey = "UNKNOWN"; // Fallback
                    logger.warn("Could not determine project key for board: {} (ID: {})", boardName, boardId);
                }
            }

            // Create board entity - all boards from JIRA are initially active
            Board board = new Board();
            board.setBoardId(boardId);
            board.setBoardName(boardName);
            board.setProjectKey(projectKey);
            board.setIsActive(true); // Boards fetched from JIRA are considered active
            board.setCreatedAt(LocalDateTime.now());
            board.setUpdatedAt(LocalDateTime.now());

            logger.debug("Processed board: {} (ID: {}, Project: {}, Type: {})",
                        boardName, boardId, projectKey, boardType);

            return board;

        } catch (Exception e) {
            logger.error("Error processing board JSON: {}", boardNode, e);
            return null;
        }
    }

    /**
     * Updates an existing board with new information from JIRA.
     *
     * @param existingBoard Current board in database
     * @param newBoardData New board data from JIRA
     * @return Updated board entity
     */
    private Board updateExistingBoard(Board existingBoard, Board newBoardData) {
        boolean updated = false;

        // Update board name if changed
        if (!existingBoard.getBoardName().equals(newBoardData.getBoardName())) {
            existingBoard.setBoardName(newBoardData.getBoardName());
            updated = true;
        }

        // Update project key if changed
        if (!existingBoard.getProjectKey().equals(newBoardData.getProjectKey())) {
            existingBoard.setProjectKey(newBoardData.getProjectKey());
            updated = true;
        }

        // Reactivate board if it was deactivated but exists in JIRA
        if (!existingBoard.getIsActive()) {
            existingBoard.setIsActive(true);
            updated = true;
            logger.info("Reactivated board: {} (ID: {})", existingBoard.getBoardName(), existingBoard.getBoardId());
        }

        if (updated) {
            existingBoard.setUpdatedAt(LocalDateTime.now());
        }

        return existingBoard;
    }

    /**
     * Deactivates boards that are no longer present in JIRA.
     *
     * @param activeBoardIds Set of board IDs currently active in JIRA
     * @return Number of boards deactivated
     */
    private int deactivateRemovedBoards(Set<Long> activeBoardIds) {
        try {
            // Find all currently active boards in database
            var allActiveBoards = boardRepository.findAllActiveBoards();
            int deactivatedCount = 0;

            for (Board board : allActiveBoards) {
                if (!activeBoardIds.contains(board.getBoardId())) {
                    // Board exists in database but not in JIRA - deactivate it
                    board.setIsActive(false);
                    board.setUpdatedAt(LocalDateTime.now());
                    boardRepository.save(board);
                    deactivatedCount++;
                    logger.info("Deactivated board: {} (ID: {}) - no longer exists in JIRA",
                               board.getBoardName(), board.getBoardId());
                }
            }

            return deactivatedCount;

        } catch (Exception e) {
            logger.error("Error deactivating removed boards", e);
            return 0;
        }
    }

    /**
     * Attempts to extract project key from board name using common patterns.
     *
     * @param boardName The board name
     * @return Extracted project key or null if not found
     */
    private String extractProjectKeyFromName(String boardName) {
        if (boardName == null || boardName.isEmpty()) {
            return null;
        }

        // Try common patterns for project keys in board names
        // Pattern 1: "PROJECT - Board Name" or "PROJECT: Board Name"
        if (boardName.contains(" - ") || boardName.contains(": ")) {
            String[] parts = boardName.split("\\s*[-:]\\s*");
            if (parts.length > 0 && parts[0].length() >= 2 && parts[0].length() <= 10) {
                String candidate = parts[0].toUpperCase().trim();
                if (candidate.matches("^[A-Z][A-Z0-9]*$")) {
                    return candidate;
                }
            }
        }

        // Pattern 2: "PROJECT Board" or "PROJECTBoard"
        String[] words = boardName.split("\\s+");
        if (words.length > 0) {
            String firstWord = words[0].toUpperCase().trim();
            if (firstWord.length() >= 2 && firstWord.length() <= 10 && firstWord.matches("^[A-Z][A-Z0-9]*$")) {
                return firstWord;
            }
        }

        // Pattern 3: Extract uppercase letters (e.g., "My Project Board" -> "MPB")
        String abbreviated = boardName.replaceAll("[^A-Z]", "");
        if (abbreviated.length() >= 2 && abbreviated.length() <= 6) {
            return abbreviated;
        }

        return null;
    }

    /**
     * Gets the count of active boards in the database.
     *
     * @return Number of active boards
     */
    public long getActiveBoardCount() {
        return boardRepository.findAllActiveBoards().size();
    }

    /**
     * Gets the timestamp of the last synchronization.
     * This can be enhanced to store actual timestamps in the future.
     *
     * @return Current timestamp (placeholder implementation)
     */
    public LocalDateTime getLastSynchronizationTime() {
        // This is a placeholder - in a real implementation, you might store this in the database
        return LocalDateTime.now();
    }
}

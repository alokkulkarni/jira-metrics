package com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.entity.Sprint;
import com.example.metrics.jira_metrics.repository.SprintRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for synchronizing JIRA sprints with the local database.
 * Handles sprint data retrieval and storage for boards that support sprints.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Service
@Transactional
public class SprintSynchronizationService {

    private static final Logger logger = LoggerFactory.getLogger(SprintSynchronizationService.class);

    private final JiraClientService jiraClientService;
    private final SprintRepository sprintRepository;

    /**
     * Constructor for SprintSynchronizationService.
     *
     * @param jiraClientService JIRA client service for API calls
     * @param sprintRepository Repository for sprint operations
     */
    public SprintSynchronizationService(JiraClientService jiraClientService,
                                      SprintRepository sprintRepository) {
        this.jiraClientService = jiraClientService;
        this.sprintRepository = sprintRepository;
    }

    /**
     * Synchronizes sprints for a specific board.
     * Only processes boards that have sprints enabled.
     *
     * @param boardId The board ID to synchronize sprints for
     * @param hasSprintsEnabled Whether the board has sprints enabled
     * @return Number of sprints synchronized
     */
    public int synchronizeSprintsForBoard(Long boardId, boolean hasSprintsEnabled) {
        if (!hasSprintsEnabled) {
            logger.debug("Board {} does not have sprints enabled, skipping sprint synchronization", boardId);
            return 0;
        }

        try {
            logger.debug("Synchronizing sprints for board ID: {}", boardId);

            Optional<JsonNode> sprintsData = jiraClientService.getBoardSprints(boardId);
            if (sprintsData.isEmpty()) {
                logger.warn("No sprints data retrieved for board ID: {}", boardId);
                return 0;
            }

            JsonNode sprintsJson = sprintsData.get();
            JsonNode sprintValues = sprintsJson.path("values");

            if (sprintValues.isMissingNode() || !sprintValues.isArray()) {
                logger.warn("Invalid sprints data structure for board ID: {}", boardId);
                return 0;
            }

            List<Sprint> sprintsToSave = new ArrayList<>();

            for (JsonNode sprintNode : sprintValues) {
                try {
                    Sprint sprint = mapJsonToSprint(sprintNode, boardId);
                    if (sprint != null) {
                        sprintsToSave.add(sprint);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to process sprint data for board {}: {}", boardId, e.getMessage());
                }
            }

            // Save all sprints for this board
            if (!sprintsToSave.isEmpty()) {
                sprintRepository.saveAll(sprintsToSave);
                logger.info("Synchronized {} sprints for board ID: {}", sprintsToSave.size(), boardId);
            }

            return sprintsToSave.size();

        } catch (Exception e) {
            logger.error("Error synchronizing sprints for board ID {}: {}", boardId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Maps JIRA sprint JSON data to Sprint entity.
     * Since Sprint is a record (immutable), creates new instances.
     *
     * @param sprintNode JSON node containing sprint data
     * @param boardId The board ID this sprint belongs to
     * @return Sprint entity or null if mapping fails
     */
    private Sprint mapJsonToSprint(JsonNode sprintNode, Long boardId) {
        try {
            Long sprintId = sprintNode.path("id").asLong();
            String sprintName = sprintNode.path("name").asText();
            String state = sprintNode.path("state").asText().toLowerCase();

            // Parse dates
            LocalDateTime startDate = null;
            String startDateStr = sprintNode.path("startDate").asText();
            if (!startDateStr.isEmpty() && !"null".equals(startDateStr)) {
                startDate = parseJiraDateTime(startDateStr);
            }

            LocalDateTime endDate = null;
            String endDateStr = sprintNode.path("endDate").asText();
            if (!endDateStr.isEmpty() && !"null".equals(endDateStr)) {
                endDate = parseJiraDateTime(endDateStr);
            }

            LocalDateTime completeDate = null;
            String completeDateStr = sprintNode.path("completeDate").asText();
            if (!completeDateStr.isEmpty() && !"null".equals(completeDateStr)) {
                completeDate = parseJiraDateTime(completeDateStr);
            }

            String goal = sprintNode.path("goal").asText();
            if (goal.isEmpty() || "null".equals(goal)) {
                goal = null;
            }

            LocalDateTime now = LocalDateTime.now();

            // Check if sprint already exists to preserve ID and createdAt
            Optional<Sprint> existingSprint = sprintRepository.findBySprintId(sprintId);
            Long id = existingSprint.map(Sprint::id).orElse(null);
            LocalDateTime createdAt = existingSprint.map(Sprint::createdAt).orElse(now);

            // Create new Sprint record with all required fields
            return new Sprint(
                id,                    // database ID (null for new records)
                sprintId,             // JIRA sprint ID
                boardId,              // board ID
                sprintName,           // sprint name
                state,                // sprint state
                startDate,            // start date
                endDate,              // end date
                completeDate,         // complete date
                goal,                 // sprint goal
                createdAt,            // created timestamp
                now                   // updated timestamp
            );

        } catch (Exception e) {
            logger.error("Error mapping sprint JSON to entity: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parses JIRA datetime format to LocalDateTime.
     *
     * @param dateTimeStr JIRA datetime string
     * @return LocalDateTime or null if parsing fails
     */
    private LocalDateTime parseJiraDateTime(String dateTimeStr) {
        try {
            // JIRA typically returns dates in ISO format with timezone
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateTimeStr);
            return zonedDateTime.toLocalDateTime();
        } catch (Exception e) {
            logger.warn("Failed to parse JIRA datetime '{}': {}", dateTimeStr, e.getMessage());
            return null;
        }
    }

    /**
     * Gets the latest sprint for a board.
     * Useful for determining active sprint for issue assignment.
     *
     * @param boardId The board ID
     * @return Optional containing the latest sprint
     */
    public Optional<Sprint> getLatestSprintForBoard(Long boardId) {
        return sprintRepository.findTopByBoardIdOrderByStartDateDesc(boardId);
    }

    /**
     * Gets the active sprint for a board.
     * Returns sprint with state 'active'.
     *
     * @param boardId The board ID
     * @return Optional containing the active sprint
     */
    public Optional<Sprint> getActiveSprintForBoard(Long boardId) {
        return sprintRepository.findFirstByBoardIdAndSprintState(boardId, "active");
    }
}

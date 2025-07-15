package com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.entity.Issue;
import com.example.metrics.jira_metrics.repository.IssueRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for synchronizing JIRA issues with the local database.
 * Handles issue data retrieval and proper linking to boards and sprints.
 * Supports both sprint-based boards (Scrum) and non-sprint boards (Kanban).
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Service
@Transactional
public class IssueSynchronizationService {

    private static final Logger logger = LoggerFactory.getLogger(IssueSynchronizationService.class);
    private static final int ISSUES_PER_PAGE = 50;
    private static final int MAX_ISSUES_PER_BOARD = 10000; // Safety limit

    private final JiraClientService jiraClientService;
    private final IssueRepository issueRepository;
    private final SprintSynchronizationService sprintSynchronizationService;

    /**
     * Constructor for IssueSynchronizationService.
     *
     * @param jiraClientService JIRA client service for API calls
     * @param issueRepository Repository for issue operations
     * @param sprintSynchronizationService Service for sprint operations
     */
    public IssueSynchronizationService(JiraClientService jiraClientService,
                                     IssueRepository issueRepository,
                                     SprintSynchronizationService sprintSynchronizationService) {
        this.jiraClientService = jiraClientService;
        this.issueRepository = issueRepository;
        this.sprintSynchronizationService = sprintSynchronizationService;
    }

    /**
     * Synchronizes issues for a specific board with proper sprint linking.
     * Handles both sprint-based and non-sprint boards appropriately.
     *
     * @param boardId The board ID to synchronize issues for
     * @param hasSprintsEnabled Whether the board has sprints enabled
     * @param boardType The type of board (scrum, kanban, simple)
     * @return Number of issues synchronized
     */
    public int synchronizeIssuesForBoard(Long boardId, boolean hasSprintsEnabled, String boardType) {
        try {
            logger.info("Starting issue synchronization for board ID: {} (type: {}, has sprints: {})",
                       boardId, boardType, hasSprintsEnabled);

            List<Issue> allIssues = new ArrayList<>();
            int startAt = 0;
            boolean hasMoreIssues = true;
            int pageCount = 0;

            // Fetch all issues with pagination
            while (hasMoreIssues && allIssues.size() < MAX_ISSUES_PER_BOARD) {
                pageCount++;
                logger.debug("Fetching issues page {} for board ID: {}", pageCount, boardId);

                Optional<JsonNode> issuesData = jiraClientService.getBoardIssues(boardId, startAt, ISSUES_PER_PAGE);

                if (issuesData.isEmpty()) {
                    logger.warn("No issues data retrieved for board ID: {} at startAt: {}", boardId, startAt);
                    break;
                }

                JsonNode issuesJson = issuesData.get();
                JsonNode issueValues = issuesJson.path("issues");

                if (issueValues.isMissingNode() || !issueValues.isArray()) {
                    logger.warn("Invalid issues data structure for board ID: {}", boardId);
                    break;
                }

                int currentPageSize = issueValues.size();
                if (currentPageSize == 0) {
                    hasMoreIssues = false;
                    break;
                }

                // Process issues from current page
                for (JsonNode issueNode : issueValues) {
                    try {
                        Issue issue = mapJsonToIssue(issueNode, boardId, hasSprintsEnabled);
                        if (issue != null) {
                            allIssues.add(issue);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to process issue for board {}: {}", boardId, e.getMessage());
                    }
                }

                // Check if more pages exist
                int total = issuesJson.path("total").asInt(-1);
                startAt += currentPageSize;

                if (total > 0 && startAt >= total) {
                    hasMoreIssues = false;
                } else if (currentPageSize < ISSUES_PER_PAGE) {
                    hasMoreIssues = false;
                }

                logger.debug("Processed page {} for board {}: {} issues, total so far: {}",
                           pageCount, boardId, currentPageSize, allIssues.size());
            }

            // Save all issues for this board
            if (!allIssues.isEmpty()) {
                issueRepository.saveAll(allIssues);
                logger.info("Synchronized {} issues for board ID: {} across {} pages",
                           allIssues.size(), boardId, pageCount);
            } else {
                logger.info("No issues found for board ID: {}", boardId);
            }

            return allIssues.size();

        } catch (Exception e) {
            logger.error("Error synchronizing issues for board ID {}: {}", boardId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Maps JIRA issue JSON data to Issue entity with proper sprint linking.
     * Since Issue is a record (immutable), creates new instances.
     *
     * @param issueNode JSON node containing issue data
     * @param boardId The board ID this issue belongs to
     * @param hasSprintsEnabled Whether the board supports sprints
     * @return Issue entity or null if mapping fails
     */
    private Issue mapJsonToIssue(JsonNode issueNode, Long boardId, boolean hasSprintsEnabled) {
        try {
            String issueId = issueNode.path("id").asText();
            String issueKey = issueNode.path("key").asText();

            // Handle sprint linking based on board type
            Long sprintId = null;
            if (hasSprintsEnabled) {
                sprintId = extractSprintId(issueNode);
                logger.debug("Issue {} linked to sprint: {}", issueKey, sprintId);
            } else {
                logger.debug("Issue {} linked directly to board {} (no sprints)", issueKey, boardId);
            }

            // Extract fields from issue JSON
            JsonNode fields = issueNode.path("fields");

            // Issue type
            String issueType = null;
            JsonNode issueTypeNode = fields.path("issuetype");
            if (!issueTypeNode.isMissingNode()) {
                issueType = issueTypeNode.path("name").asText();
            }

            // Status
            String status = null;
            JsonNode statusNode = fields.path("status");
            if (!statusNode.isMissingNode()) {
                status = statusNode.path("name").asText();
            }

            // Priority
            String priority = null;
            JsonNode priorityNode = fields.path("priority");
            if (!priorityNode.isMissingNode()) {
                priority = priorityNode.path("name").asText();
            }

            // Assignee
            String assigneeAccountId = null;
            String assigneeDisplayName = null;
            JsonNode assignee = fields.path("assignee");
            if (!assignee.isMissingNode() && !assignee.isNull()) {
                assigneeAccountId = assignee.path("accountId").asText();
                assigneeDisplayName = assignee.path("displayName").asText();
            }

            // Reporter
            String reporterAccountId = null;
            String reporterDisplayName = null;
            JsonNode reporter = fields.path("reporter");
            if (!reporter.isMissingNode() && !reporter.isNull()) {
                reporterAccountId = reporter.path("accountId").asText();
                reporterDisplayName = reporter.path("displayName").asText();
            }

            // Summary and description
            String summary = fields.path("summary").asText();
            String description = fields.path("description").asText();

            // Story points
            BigDecimal storyPoints = null;
            JsonNode storyPointsNode = fields.path("customfield_10016"); // Common story points field
            if (!storyPointsNode.isMissingNode() && !storyPointsNode.isNull()) {
                try {
                    storyPoints = BigDecimal.valueOf(storyPointsNode.asDouble());
                } catch (Exception e) {
                    logger.debug("Could not parse story points for issue {}: {}", issueKey, e.getMessage());
                }
            }

            // Time tracking
            Long originalEstimate = 0L;
            Long remainingEstimate = 0L;
            Long timeSpent = 0L;
            JsonNode timeTracking = fields.path("timetracking");
            if (!timeTracking.isMissingNode()) {
                originalEstimate = timeTracking.path("originalEstimateSeconds").asLong(0);
                remainingEstimate = timeTracking.path("remainingEstimateSeconds").asLong(0);
                timeSpent = timeTracking.path("timeSpentSeconds").asLong(0);
            }

            // Dates
            LocalDateTime createdDate = null;
            String createdDateStr = fields.path("created").asText();
            if (!createdDateStr.isEmpty()) {
                createdDate = parseJiraDateTime(createdDateStr);
            }

            LocalDateTime updatedDate = null;
            String updatedDateStr = fields.path("updated").asText();
            if (!updatedDateStr.isEmpty()) {
                updatedDate = parseJiraDateTime(updatedDateStr);
            }

            LocalDateTime resolvedDate = null;
            String resolvedDateStr = fields.path("resolutiondate").asText();
            if (!resolvedDateStr.isEmpty() && !"null".equals(resolvedDateStr)) {
                resolvedDate = parseJiraDateTime(resolvedDateStr);
            }

            LocalDateTime dueDate = null;
            String dueDateStr = fields.path("duedate").asText();
            if (!dueDateStr.isEmpty() && !"null".equals(dueDateStr)) {
                dueDate = parseJiraDateTime(dueDateStr);
            }

            // Labels, components, fix versions as JSON strings
            String labels = null;
            JsonNode labelsNode = fields.path("labels");
            if (labelsNode.isArray()) {
                labels = labelsNode.toString();
            }

            String components = null;
            JsonNode componentsNode = fields.path("components");
            if (componentsNode.isArray()) {
                components = componentsNode.toString();
            }

            String fixVersions = null;
            JsonNode fixVersionsNode = fields.path("fixVersions");
            if (fixVersionsNode.isArray()) {
                fixVersions = fixVersionsNode.toString();
            }

            LocalDateTime now = LocalDateTime.now();

            // Check if issue already exists to preserve ID and createdAt
            Optional<Issue> existingIssue = issueRepository.findByIssueId(issueId);
            Long id = existingIssue.map(Issue::id).orElse(null);
            LocalDateTime createdAt = existingIssue.map(Issue::createdAt).orElse(now);

            // Create new Issue record with all required fields
            return new Issue(
                id,                    // database ID (null for new records)
                issueId,              // JIRA issue ID
                issueKey,             // JIRA issue key
                boardId,              // board ID
                sprintId,             // sprint ID (null for non-sprint boards)
                issueType,            // issue type
                status,               // status
                priority,             // priority
                assigneeAccountId,    // assignee account ID
                assigneeDisplayName,  // assignee display name
                reporterAccountId,    // reporter account ID
                reporterDisplayName,  // reporter display name
                summary,              // summary
                description,          // description
                storyPoints,          // story points
                originalEstimate,     // original estimate
                remainingEstimate,    // remaining estimate
                timeSpent,            // time spent
                createdDate,          // created date
                updatedDate,          // updated date
                resolvedDate,         // resolved date
                dueDate,              // due date
                labels,               // labels
                components,           // components
                fixVersions,          // fix versions
                createdAt,            // created timestamp
                now                   // updated timestamp
            );

        } catch (Exception e) {
            logger.error("Error mapping issue JSON to entity: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extracts sprint ID from issue JSON.
     * Handles various sprint field formats used by JIRA.
     *
     * @param issueNode The issue JSON node
     * @return Sprint ID or null if no sprint found
     */
    private Long extractSprintId(JsonNode issueNode) {
        try {
            JsonNode fields = issueNode.path("fields");

            // Try different sprint field names (JIRA can use different custom fields)
            String[] sprintFieldNames = {
                "customfield_10020", // Common sprint field
                "customfield_10010", // Alternative sprint field
                "sprint",
                "sprints"
            };

            for (String fieldName : sprintFieldNames) {
                JsonNode sprintField = fields.path(fieldName);

                if (!sprintField.isMissingNode() && !sprintField.isNull()) {
                    if (sprintField.isArray() && sprintField.size() > 0) {
                        // Handle array of sprints - take the last (most recent) one
                        JsonNode lastSprint = sprintField.get(sprintField.size() - 1);
                        Long sprintId = extractSprintIdFromNode(lastSprint);
                        if (sprintId != null) {
                            return sprintId;
                        }
                    } else if (sprintField.isObject()) {
                        // Handle single sprint object
                        Long sprintId = extractSprintIdFromNode(sprintField);
                        if (sprintId != null) {
                            return sprintId;
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.debug("Error extracting sprint ID from issue: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Extracts sprint ID from a sprint node.
     *
     * @param sprintNode The sprint JSON node
     * @return Sprint ID or null if not found
     */
    private Long extractSprintIdFromNode(JsonNode sprintNode) {
        if (sprintNode.has("id")) {
            return sprintNode.path("id").asLong();
        }

        // Sometimes sprint is stored as a string containing sprint info
        if (sprintNode.isTextual()) {
            String sprintText = sprintNode.asText();
            // Parse sprint string format like: "com.atlassian.greenhopper.service.sprint.Sprint@1234[id=123,...]"
            if (sprintText.contains("id=")) {
                try {
                    int idStart = sprintText.indexOf("id=") + 3;
                    int idEnd = sprintText.indexOf(",", idStart);
                    if (idEnd == -1) {
                        idEnd = sprintText.indexOf("]", idStart);
                    }
                    if (idEnd > idStart) {
                        String idStr = sprintText.substring(idStart, idEnd);
                        return Long.parseLong(idStr);
                    }
                } catch (Exception e) {
                    logger.debug("Could not parse sprint ID from text: {}", sprintText);
                }
            }
        }

        return null;
    }

    /**
     * Parses JIRA datetime format to LocalDateTime.
     *
     * @param dateTimeStr JIRA datetime string
     * @return LocalDateTime or null if parsing fails
     */
    private LocalDateTime parseJiraDateTime(String dateTimeStr) {
        try {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateTimeStr);
            return zonedDateTime.toLocalDateTime();
        } catch (Exception e) {
            logger.debug("Failed to parse JIRA datetime '{}': {}", dateTimeStr, e.getMessage());
            return null;
        }
    }
}

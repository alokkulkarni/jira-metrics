package com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.entity.*;
import com.example.metrics.jira_metrics.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service for processing and storing comprehensive JIRA data including board details,
 * sprints, issues, and calculating metrics insights.
 * Handles data transformation, persistence, and metrics calculation operations.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Service
@Transactional
public class JiraDataService {

    private static final Logger logger = LoggerFactory.getLogger(JiraDataService.class);
    private static final String DATA_TYPE_ISSUES = "issues";
    private static final String DATA_TYPE_SPRINTS = "sprints";
    private static final String DATA_TYPE_BOARD_CONFIG = "board_config";
    private static final String DATA_TYPE_TEAMS = "teams";
    private static final String DATA_TYPE_BOARD_DETAILS = "board_details";

    private final JiraClientService jiraClientService;
    private final BoardRepository boardRepository;
    private final TeamRepository teamRepository;
    private final JiraDataRepository jiraDataRepository;
    private final BoardDetailsRepository boardDetailsRepository;
    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;
    private final JiraMetricsCalculationService metricsCalculationService;
    private final BoardMetricsRepository boardMetricsRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructor for JiraDataService with enhanced repositories and services.
     *
     * @param jiraClientService JIRA client service
     * @param boardRepository Board repository
     * @param teamRepository Team repository
     * @param jiraDataRepository JIRA data repository
     * @param boardDetailsRepository Board details repository
     * @param sprintRepository Sprint repository
     * @param issueRepository Issue repository
     * @param metricsCalculationService Metrics calculation service
     * @param boardMetricsRepository Board metrics repository
     * @param objectMapper JSON object mapper
     */
    public JiraDataService(JiraClientService jiraClientService,
                          BoardRepository boardRepository,
                          TeamRepository teamRepository,
                          JiraDataRepository jiraDataRepository,
                          BoardDetailsRepository boardDetailsRepository,
                          SprintRepository sprintRepository,
                          IssueRepository issueRepository,
                          JiraMetricsCalculationService metricsCalculationService,
                          BoardMetricsRepository boardMetricsRepository,
                          ObjectMapper objectMapper) {
        this.jiraClientService = jiraClientService;
        this.boardRepository = boardRepository;
        this.teamRepository = teamRepository;
        this.jiraDataRepository = jiraDataRepository;
        this.boardDetailsRepository = boardDetailsRepository;
        this.sprintRepository = sprintRepository;
        this.issueRepository = issueRepository;
        this.metricsCalculationService = metricsCalculationService;
        this.boardMetricsRepository = boardMetricsRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves and stores comprehensive data for all active boards including metrics calculation.
     * This method is called by the scheduled job.
     */
    public void processAllBoards() {
        logger.info("Starting comprehensive JIRA data processing for all active boards");

        List<Board> activeBoards = boardRepository.findAllActiveBoards();
        logger.info("Found {} active boards to process", activeBoards.size());

        for (Board board : activeBoards) {
            try {
                processBoardDataComprehensive(board);
                calculateBoardMetrics(board.getBoardId());
            } catch (Exception e) {
                logger.error("Error processing board {}: {}", board.getBoardId(), e.getMessage(), e);
            }
        }

        logger.info("Completed comprehensive JIRA data processing for all active boards");
    }

    /**
     * Processes comprehensive data for a specific board including details, sprints, and issues.
     *
     * @param board The board to process
     */
    public void processBoardDataComprehensive(Board board) {
        logger.info("Processing comprehensive data for board: {} (ID: {})",
                   board.getBoardName(), board.getBoardId());

        LocalDateTime retrievalTime = LocalDateTime.now();

        // Process enhanced board details
        processBoardDetailsEnhanced(board, retrievalTime);

        // Process board configuration (existing)
        processBoardConfiguration(board, retrievalTime);

        // Process sprints with detailed information
        processSprintsDetailed(board, retrievalTime);

        // Process issues with comprehensive data
        processIssuesDetailed(board, retrievalTime);

        // Store raw data for backup (existing functionality)
        processBoardIssues(board, retrievalTime);
        processBoardSprints(board, retrievalTime);

        logger.info("Completed comprehensive processing for board: {} (ID: {})",
                   board.getBoardName(), board.getBoardId());
    }

    /**
     * Processes and stores enhanced board details including configuration and metadata.
     * Now uses project key lookup instead of board ID.
     *
     * @param board The board to process
     * @param retrievalTime The time of data retrieval
     */
    private void processBoardDetailsEnhanced(Board board, LocalDateTime retrievalTime) {
        logger.info("Processing enhanced board details for board: {} (Project: {})",
                   board.getBoardName(), board.getProjectKey());

        try {
            // Use project key to get board configuration instead of board ID
            var boardConfigData = jiraClientService.getBoardConfigurationByProjectKey(board.getProjectKey());

            if (boardConfigData.isPresent()) {
                var configJson = boardConfigData.get();

                // Resolve actual board ID from JIRA response if not set
                Long actualBoardId = board.getBoardId();
                if (actualBoardId == null) {
                    actualBoardId = jiraClientService.findBoardIdByProjectKey(board.getProjectKey()).orElse(null);
                    if (actualBoardId != null) {
                        board.setBoardId(actualBoardId);
                        logger.info("Resolved board ID {} for project key: {}", actualBoardId, board.getProjectKey());
                    }
                }

                // Extract board details from configuration
                var boardDetails = extractBoardDetails(actualBoardId, configJson);

                // Save or update board details
                var existing = boardDetailsRepository.findByBoardId(actualBoardId);
                if (existing.isPresent()) {
                    // Update existing details
                    var updated = existing.get().withUpdates(
                            boardDetails.boardType(),
                            boardDetails.boardLocation(),
                            boardDetails.filterId(),
                            boardDetails.canEdit(),
                            boardDetails.subQuery(),
                            boardDetails.columnConfig(),
                            boardDetails.estimationConfig(),
                            boardDetails.rankingConfig()
                    );
                    boardDetailsRepository.save(updated);
                    logger.debug("Updated board details for project key: {}", board.getProjectKey());
                } else {
                    boardDetailsRepository.save(boardDetails);
                    logger.debug("Created new board details for project key: {}", board.getProjectKey());
                }

                // Store raw configuration data
                var jiraData = JiraData.create(
                        actualBoardId,
                        null,
                        DATA_TYPE_BOARD_DETAILS,
                        configJson.toString(),
                        retrievalTime,
                        1
                );
                jiraDataRepository.save(jiraData);

                logger.info("Successfully processed enhanced board details for project key: {}",
                           board.getProjectKey());
            } else {
                logger.warn("No board configuration data retrieved for project key: {}",
                           board.getProjectKey());
            }
        } catch (Exception e) {
            logger.error("Error processing board details for project key {}: {}",
                        board.getProjectKey(), e.getMessage(), e);
        }
    }

    /**
     * Processes sprints with detailed information using project key lookup.
     *
     * @param board The board to process
     * @param retrievalTime The time of data retrieval
     */
    private void processSprintsDetailed(Board board, LocalDateTime retrievalTime) {
        logger.info("Processing detailed sprints for project key: {}", board.getProjectKey());

        try {
            // Use project key to get sprints instead of board ID
            var sprintsData = jiraClientService.getBoardSprintsByProjectKey(board.getProjectKey());

            if (sprintsData.isPresent()) {
                var sprintsJson = sprintsData.get();

                // Resolve board ID if needed
                Long actualBoardId = board.getBoardId();
                if (actualBoardId == null) {
                    actualBoardId = jiraClientService.findBoardIdByProjectKey(board.getProjectKey()).orElse(null);
                }

                var sprints = extractSprintsFromJson(actualBoardId, sprintsJson);

                int processedCount = 0;
                for (var sprint : sprints) {
                    try {
                        // Save or update sprint
                        var existing = sprintRepository.findBySprintId(sprint.sprintId());
                        if (existing.isPresent()) {
                            var updated = existing.get().withUpdates(
                                    sprint.sprintName(),
                                    sprint.sprintState(),
                                    sprint.startDate(),
                                    sprint.endDate(),
                                    sprint.completeDate(),
                                    sprint.goal()
                            );
                            sprintRepository.save(updated);
                        } else {
                            sprintRepository.save(sprint);
                        }
                        processedCount++;
                    } catch (Exception e) {
                        logger.error("Error processing sprint {}: {}", sprint.sprintId(), e.getMessage());
                    }
                }

                // Store raw sprint data for backup
                String rawData = objectMapper.writeValueAsString(sprintsJson);
                var jiraData = JiraData.create(
                        actualBoardId,
                        null,
                        DATA_TYPE_SPRINTS,
                        rawData,
                        retrievalTime,
                        processedCount
                );
                jiraDataRepository.save(jiraData);

                logger.info("Successfully processed {} sprints for project key: {}",
                           processedCount, board.getProjectKey());
            } else {
                logger.warn("No sprints data retrieved for project key: {}", board.getProjectKey());
            }
        } catch (Exception e) {
            logger.error("Error processing sprints for project key {}: {}",
                        board.getProjectKey(), e.getMessage(), e);
        }
    }

    /**
     * Processes issues with comprehensive data using project key lookup.
     *
     * @param board The board to process
     * @param retrievalTime The time of data retrieval
     */
    private void processIssuesDetailed(Board board, LocalDateTime retrievalTime) {
        logger.info("Processing detailed issues for project key: {}", board.getProjectKey());

        try {
            int startAt = 0;
            int maxResults = 50;
            int totalProcessed = 0;
            boolean hasMoreData = true;

            // Resolve board ID if needed
            Long actualBoardId = board.getBoardId();
            if (actualBoardId == null) {
                actualBoardId = jiraClientService.findBoardIdByProjectKey(board.getProjectKey()).orElse(null);
            }

            while (hasMoreData) {
                // Use project key to get issues instead of board ID
                var issuesData = jiraClientService.getBoardIssuesByProjectKey(
                        board.getProjectKey(), startAt, maxResults);

                if (issuesData.isPresent()) {
                    var issuesJson = issuesData.get();
                    var issues = extractIssuesFromJson(actualBoardId, issuesJson);

                    for (var issue : issues) {
                        try {
                            // Save or update issue
                            var existing = issueRepository.findByIssueId(issue.issueId());
                            if (existing.isEmpty()) {
                                issueRepository.save(issue);
                                totalProcessed++;
                            }
                        } catch (Exception e) {
                            logger.error("Error processing issue {}: {}", issue.issueKey(), e.getMessage());
                        }
                    }

                    // Store raw issue data for backup if this is the first batch
                    if (startAt == 0) {
                        String rawData = objectMapper.writeValueAsString(issuesJson);
                        var jiraData = JiraData.create(
                                actualBoardId,
                                null,
                                DATA_TYPE_ISSUES,
                                rawData,
                                retrievalTime,
                                issues.size()
                        );
                        jiraDataRepository.save(jiraData);
                    }

                    // Check if there are more issues to fetch
                    var totalNode = issuesJson.get("total");
                    if (totalNode != null) {
                        int total = totalNode.asInt();
                        startAt += maxResults;
                        hasMoreData = startAt < total;
                    } else {
                        hasMoreData = false;
                    }
                } else {
                    hasMoreData = false;
                }
            }

            logger.info("Successfully processed {} issues for project key: {}",
                       totalProcessed, board.getProjectKey());
        } catch (Exception e) {
            logger.error("Error processing issues for project key {}: {}",
                        board.getProjectKey(), e.getMessage(), e);
        }
    }

    /**
     * Calculates metrics for a board using the metrics calculation service.
     * This method is public to allow manual triggering via API endpoints.
     * Now calculates metrics for both active and completed sprints.
     *
     * @param boardId The board ID to calculate metrics for
     */
    public void calculateBoardMetrics(Long boardId) {
        logger.info("Calculating metrics for board ID: {}", boardId);

        try {
            // Calculate metrics for all sprints (active and completed)
            var allSprints = sprintRepository.findByBoardId(boardId);
            logger.info("Found {} total sprints for board {}", allSprints.size(), boardId);

            if (allSprints.isEmpty()) {
                logger.warn("No sprints found for board {}, cannot calculate metrics", boardId);
                return;
            }

            int metricsCalculated = 0;
            int activeSprintsProcessed = 0;
            int completedSprintsProcessed = 0;

            for (var sprint : allSprints) {
                try {
                    var isCompleted = sprint.isCompleted();
                    var calculatedMetrics = metricsCalculationService.calculateSprintMetrics(sprint.sprintId());

                    if (calculatedMetrics.isPresent()) {
                        logger.debug("Calculated {} metrics for sprint {}",
                                   isCompleted ? "final" : "progress", sprint.sprintId());
                        metricsCalculated++;

                        if (isCompleted) {
                            completedSprintsProcessed++;
                        } else {
                            activeSprintsProcessed++;
                        }
                    } else {
                        logger.warn("Failed to calculate metrics for sprint {}", sprint.sprintId());
                    }
                } catch (Exception e) {
                    logger.error("Error calculating metrics for sprint {}: {}",
                               sprint.sprintId(), e.getMessage());
                }
            }

            // Calculate overall board metrics using completed sprints only
            var completedSprints = sprintRepository.findByBoardIdAndSprintState(boardId, "CLOSED");
            if (!completedSprints.isEmpty()) {
                metricsCalculationService.calculateBoardMetrics(boardId, Math.min(5, completedSprints.size()));
            }

            logger.info("Successfully calculated metrics for {} sprints on board ID: {} " +
                       "({} completed, {} active)",
                       metricsCalculated, boardId, completedSprintsProcessed, activeSprintsProcessed);

        } catch (Exception e) {
            logger.error("Error calculating metrics for board ID {}: {}", boardId, e.getMessage(), e);
            throw e; // Re-throw to let caller handle the error
        }
    }


    /**
     * Processes data for a specific board.
     *
     * @param board The board to process
     */
    public void processBoardData(Board board) {
        logger.info("Processing data for board: {} (ID: {})", board.getBoardName(), board.getBoardId());

        LocalDateTime retrievalTime = LocalDateTime.now();

        // Retrieve and store board configuration
        processBoardConfiguration(board, retrievalTime);

        // Retrieve and store board issues
        processBoardIssues(board, retrievalTime);

        // Retrieve and store board sprints
        processBoardSprints(board, retrievalTime);

        logger.info("Completed processing data for board: {} (ID: {})",
                   board.getBoardName(), board.getBoardId());
    }

    /**
     * Processes board configuration data.
     *
     * @param board         The board
     * @param retrievalTime The retrieval timestamp
     */
    private void processBoardConfiguration(Board board, LocalDateTime retrievalTime) {
        logger.debug("Processing board configuration for project key: {}", board.getProjectKey());

        // Use project key to get configuration instead of board ID
        Optional<JsonNode> configData = jiraClientService.getBoardConfigurationByProjectKey(board.getProjectKey());
        if (configData.isPresent()) {
            try {
                String rawData = objectMapper.writeValueAsString(configData.get());

                // Resolve board ID if needed
                Long actualBoardId = board.getBoardId();
                if (actualBoardId == null) {
                    actualBoardId = jiraClientService.findBoardIdByProjectKey(board.getProjectKey()).orElse(null);
                }

                JiraData jiraData = new JiraData(actualBoardId, DATA_TYPE_BOARD_CONFIG,
                                               rawData, retrievalTime);
                jiraDataRepository.save(jiraData);

                logger.debug("Saved board configuration for project key: {}", board.getProjectKey());
            } catch (Exception e) {
                logger.error("Error saving board configuration for project key: {}",
                           board.getProjectKey(), e);
            }
        } else {
            logger.warn("No board configuration data retrieved for project key: {}", board.getProjectKey());
        }
    }

    /**
     * Processes board issues data with pagination using project key lookup.
     *
     * @param board The board
     * @param retrievalTime The retrieval timestamp
     */
    private void processBoardIssues(Board board, LocalDateTime retrievalTime) {
        logger.debug("Processing board issues for project key: {}", board.getProjectKey());

        int startAt = 0;
        int maxResults = 50;
        int totalProcessed = 0;
        boolean hasMoreData = true;

        // Resolve board ID if needed
        Long actualBoardId = board.getBoardId();
        if (actualBoardId == null) {
            actualBoardId = jiraClientService.findBoardIdByProjectKey(board.getProjectKey()).orElse(null);
        }

        while (hasMoreData) {
            // Use project key to get issues instead of board ID
            Optional<JsonNode> issuesData = jiraClientService.getBoardIssuesByProjectKey(
                    board.getProjectKey(), startAt, maxResults);

            if (issuesData.isPresent()) {
                try {
                    JsonNode issues = issuesData.get();
                    String rawData = objectMapper.writeValueAsString(issues);

                    JiraData jiraData = new JiraData(actualBoardId, DATA_TYPE_ISSUES,
                                                   rawData, retrievalTime);
                    jiraData.setRecordCount(issues.path("issues").size());
                    jiraDataRepository.save(jiraData);

                    int currentBatchSize = issues.path("issues").size();
                    totalProcessed += currentBatchSize;

                    // Check if there are more results
                    int maxResultsFromResponse = issues.path("maxResults").asInt(maxResults);
                    int totalFromResponse = issues.path("total").asInt();
                    hasMoreData = (startAt + maxResultsFromResponse) < totalFromResponse;
                    startAt += maxResults;

                    logger.debug("Processed {} issues for project key: {} (batch size: {})",
                               totalProcessed, board.getProjectKey(), currentBatchSize);

                } catch (Exception e) {
                    logger.error("Error saving board issues for project key: {}",
                               board.getProjectKey(), e);
                    hasMoreData = false;
                }
            } else {
                logger.warn("No issues data retrieved for project key: {} at startAt: {}",
                          board.getProjectKey(), startAt);
                hasMoreData = false;
            }
        }

        logger.debug("Completed processing {} total issues for project key: {}",
                   totalProcessed, board.getProjectKey());
    }

    /**
     * Processes board sprints data using project key lookup.
     *
     * @param board The board
     * @param retrievalTime The retrieval timestamp
     */
    private void processBoardSprints(Board board, LocalDateTime retrievalTime) {
        logger.debug("Processing board sprints for project key: {}", board.getProjectKey());

        // Use project key to get sprints instead of board ID
        Optional<JsonNode> sprintsData = jiraClientService.getBoardSprintsByProjectKey(board.getProjectKey());
        if (sprintsData.isPresent()) {
            try {
                JsonNode sprints = sprintsData.get();
                String rawData = objectMapper.writeValueAsString(sprints);

                // Resolve board ID if needed
                Long actualBoardId = board.getBoardId();
                if (actualBoardId == null) {
                    actualBoardId = jiraClientService.findBoardIdByProjectKey(board.getProjectKey()).orElse(null);
                }

                JiraData jiraData = new JiraData(actualBoardId, DATA_TYPE_SPRINTS,
                                               rawData, retrievalTime);
                jiraData.setRecordCount(sprints.path("values").size());
                jiraDataRepository.save(jiraData);

                logger.debug("Saved {} sprints for project key: {}",
                           sprints.path("values").size(), board.getProjectKey());
            } catch (Exception e) {
                logger.error("Error saving board sprints for project key: {}",
                           board.getProjectKey(), e);
            }
        } else {
            logger.warn("No sprints data retrieved for project key: {}", board.getProjectKey());
        }
    }

    /**
     * Processes and stores team information from JIRA.
     */
    public void processTeamData() {
        logger.info("Starting team data processing");

        Optional<JsonNode> teamsData = jiraClientService.getAllTeams();
        if (teamsData.isPresent()) {
            try {
                JsonNode teams = teamsData.get();
                String rawData = objectMapper.writeValueAsString(teams);

                // Store raw team data
                JiraData jiraData = new JiraData(null, DATA_TYPE_TEAMS, rawData, LocalDateTime.now());
                jiraData.setRecordCount(teams.path("values").size());
                jiraDataRepository.save(jiraData);

                // Process individual teams
                JsonNode teamValues = teams.path("values");
                int processedCount = 0;

                for (JsonNode teamNode : teamValues) {
                    processTeamEntity(teamNode);
                    processedCount++;
                }

                logger.info("Completed processing {} teams", processedCount);

            } catch (Exception e) {
                logger.error("Error processing team data", e);
            }
        } else {
            logger.warn("No team data retrieved from JIRA");
        }
    }

    /**
     * Processes and saves individual team entity.
     *
     * @param teamNode JSON node containing team data
     */
    private void processTeamEntity(JsonNode teamNode) {
        try {
            String teamId = teamNode.path("id").asText();
            String teamName = teamNode.path("name").asText();

            if (teamId.isEmpty() || teamName.isEmpty()) {
                logger.warn("Skipping team with missing ID or name: {}", teamNode);
                return;
            }

            Optional<Team> existingTeam = teamRepository.findByTeamId(teamId);
            Team team = existingTeam.orElse(new Team(teamId, teamName));

            // Update team details
            team.setTeamName(teamName);
            team.setDescription(teamNode.path("description").asText(null));
            team.setUpdatedAt(LocalDateTime.now());

            JsonNode lead = teamNode.path("lead");
            if (!lead.isMissingNode()) {
                team.setLeadAccountId(lead.path("accountId").asText(null));
                team.setLeadDisplayName(lead.path("displayName").asText(null));
            }

            JsonNode members = teamNode.path("members");
            if (!members.isMissingNode()) {
                team.setMemberCount(members.path("size").asInt(0));
            }

            teamRepository.save(team);
            logger.debug("Saved team: {} (ID: {})", teamName, teamId);

        } catch (Exception e) {
            logger.error("Error processing team entity: {}", teamNode, e);
        }
    }

    /**
     * Extracts board details from JSON configuration data.
     *
     * @param boardId The board ID
     * @param configJson The JSON configuration data
     * @return BoardDetails entity
     */
    private BoardDetails extractBoardDetails(Long boardId, JsonNode configJson) {
        try {
            String boardType = configJson.path("type").asText("unknown");
            String boardLocation = configJson.path("location").path("type").asText("unknown");
            Long filterId = configJson.path("filter").path("id").isNull() ? null : configJson.path("filter").path("id").asLong();
            boolean canEdit = configJson.path("canEdit").asBoolean(false);
            String subQuery = configJson.path("subQuery").asText(null);

            // Extract column configuration
            String columnConfig = null;
            JsonNode columnConfigNode = configJson.path("columnConfig");
            if (!columnConfigNode.isMissingNode()) {
                columnConfig = columnConfigNode.toString();
            }

            // Extract estimation configuration
            String estimationConfig = null;
            JsonNode estimationNode = configJson.path("estimation");
            if (!estimationNode.isMissingNode()) {
                estimationConfig = estimationNode.toString();
            }

            // Extract ranking configuration
            String rankingConfig = null;
            JsonNode rankingNode = configJson.path("ranking");
            if (!rankingNode.isMissingNode()) {
                rankingConfig = rankingNode.toString();
            }

            return BoardDetails.create(
                    boardId,
                    boardType,
                    boardLocation,
                    filterId,
                    canEdit,
                    subQuery,
                    columnConfig,
                    estimationConfig,
                    rankingConfig
            );

        } catch (Exception e) {
            logger.error("Error extracting board details from JSON: {}", e.getMessage(), e);
            // Return minimal board details if extraction fails
            return BoardDetails.create(boardId, "unknown", "unknown", null, false, null, null, null, null);
        }
    }

    /**
     * Extracts sprints from JSON data.
     *
     * @param boardId The board ID
     * @param sprintsJson The JSON sprints data
     * @return List of Sprint entities
     */
    private List<Sprint> extractSprintsFromJson(Long boardId, JsonNode sprintsJson) {
        try {
            JsonNode sprintValues = sprintsJson.path("values");
            if (sprintValues.isMissingNode()) {
                logger.warn("No sprint values found in JSON for board ID: {}", boardId);
                return List.of();
            }

            return StreamSupport.stream(sprintValues.spliterator(), false)
                    .map(sprintNode -> extractSprintFromNode(boardId, sprintNode))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error extracting sprints from JSON for board ID {}: {}", boardId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Extracts a single sprint from a JSON node.
     *
     * @param boardId The board ID
     * @param sprintNode The JSON node containing sprint data
     * @return Optional Sprint entity
     */
    private Optional<Sprint> extractSprintFromNode(Long boardId, JsonNode sprintNode) {
        try {
            Long sprintId = sprintNode.path("id").asLong();
            String sprintName = sprintNode.path("name").asText();
            String sprintState = sprintNode.path("state").asText();

            if (sprintId == 0 || sprintName.isEmpty()) {
                logger.warn("Invalid sprint data - missing ID or name: {}", sprintNode);
                return Optional.empty();
            }

            // Parse dates with proper error handling
            LocalDateTime startDate = parseDateTime(sprintNode.path("startDate").asText(null));
            LocalDateTime endDate = parseDateTime(sprintNode.path("endDate").asText(null));
            LocalDateTime completeDate = parseDateTime(sprintNode.path("completeDate").asText(null));

            String goal = sprintNode.path("goal").asText(null);

            return Optional.of(Sprint.create(
                    sprintId,
                    boardId,
                    sprintName,
                    sprintState,
                    startDate,
                    endDate,
                    completeDate,
                    goal
            ));

        } catch (Exception e) {
            logger.error("Error extracting sprint from node: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts issues from JSON data.
     *
     * @param boardId The board ID
     * @param issuesJson The JSON issues data
     * @return List of Issue entities
     */
    private List<Issue> extractIssuesFromJson(Long boardId, JsonNode issuesJson) {
        try {
            JsonNode issueValues = issuesJson.path("issues");
            if (issueValues.isMissingNode()) {
                logger.warn("No issue values found in JSON for board ID: {}", boardId);
                return List.of();
            }

            return StreamSupport.stream(issueValues.spliterator(), false)
                    .map(issueNode -> extractIssueFromNode(boardId, issueNode))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error extracting issues from JSON for board ID {}: {}", boardId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Extracts a single issue from a JSON node.
     *
     * @param boardId The board ID
     * @param issueNode The JSON node containing issue data
     * @return Optional Issue entity
     */
    private Optional<Issue> extractIssueFromNode(Long boardId, JsonNode issueNode) {
        try {
            String issueId = issueNode.path("id").asText();
            String issueKey = issueNode.path("key").asText();

            if (issueId.isEmpty() || issueKey.isEmpty()) {
                logger.warn("Invalid issue data - missing ID or key: {}", issueNode);
                return Optional.empty();
            }

            JsonNode fields = issueNode.path("fields");
            String summary = fields.path("summary").asText("");
            String description = fields.path("description").asText(null);

            // Extract issue type as String
            JsonNode issueTypeNode = fields.path("issuetype");
            String issueType = issueTypeNode.path("name").asText("Task");

            // Extract status as String
            JsonNode statusNode = fields.path("status");
            String status = statusNode.path("name").asText("To Do");

            // Extract assignee
            JsonNode assigneeNode = fields.path("assignee");
            String assigneeAccountId = null;
            String assigneeDisplayName = null;
            if (!assigneeNode.isMissingNode() && !assigneeNode.isNull()) {
                assigneeAccountId = assigneeNode.path("accountId").asText(null);
                assigneeDisplayName = assigneeNode.path("displayName").asText(null);
            }

            // Extract dates
            LocalDateTime createdDate = parseDateTime(fields.path("created").asText(null));
            LocalDateTime updatedDate = parseDateTime(fields.path("updated").asText(null));
            LocalDateTime resolvedDate = parseDateTime(fields.path("resolutiondate").asText(null));

            // Extract story points
            BigDecimal storyPoints = null;
            JsonNode storyPointsNode = fields.path("customfield_10020"); // Common story points field
            if (!storyPointsNode.isMissingNode() && !storyPointsNode.isNull()) {
                storyPoints = BigDecimal.valueOf(storyPointsNode.asDouble(0.0));
            }

            // Extract sprint information
            Long sprintId = extractSprintIdFromIssue(fields);

            // Extract reporter information
            JsonNode reporterNode = fields.path("reporter");
            String reporterAccountId = null;
            String reporterDisplayName = null;
            if (!reporterNode.isMissingNode() && !reporterNode.isNull()) {
                reporterAccountId = reporterNode.path("accountId").asText(null);
                reporterDisplayName = reporterNode.path("displayName").asText(null);
            }

            // Extract priority
            JsonNode priorityNode = fields.path("priority");
            String priority = priorityNode.path("name").asText("Medium");

            // Extract labels
            JsonNode labelsNode = fields.path("labels");
            String labels = null;
            if (labelsNode.isArray() && !labelsNode.isEmpty()) {
                labels = StreamSupport.stream(labelsNode.spliterator(), false)
                        .map(JsonNode::asText)
                        .collect(Collectors.joining(","));
            }

            // Extract components
            JsonNode componentsNode = fields.path("components");
            String components = null;
            if (componentsNode.isArray() && !componentsNode.isEmpty()) {
                components = StreamSupport.stream(componentsNode.spliterator(), false)
                        .map(comp -> comp.path("name").asText())
                        .collect(Collectors.joining(","));
            }

            // Extract fix versions
            JsonNode fixVersionsNode = fields.path("fixVersions");
            String fixVersions = null;
            if (fixVersionsNode.isArray() && !fixVersionsNode.isEmpty()) {
                fixVersions = StreamSupport.stream(fixVersionsNode.spliterator(), false)
                        .map(version -> version.path("name").asText())
                        .collect(Collectors.joining(","));
            }

            // Extract due date
            LocalDateTime dueDate = parseDateTime(fields.path("duedate").asText(null));

            // Extract time tracking information
            JsonNode timeTrackingNode = fields.path("timetracking");
            Long originalEstimate = null;
            Long remainingEstimate = null;
            Long timeSpent = null;

            if (!timeTrackingNode.isMissingNode()) {
                originalEstimate = timeTrackingNode.path("originalEstimateSeconds").isNull() ? null :
                                 timeTrackingNode.path("originalEstimateSeconds").asLong();
                remainingEstimate = timeTrackingNode.path("remainingEstimateSeconds").isNull() ? null :
                                  timeTrackingNode.path("remainingEstimateSeconds").asLong();
                timeSpent = timeTrackingNode.path("timeSpentSeconds").isNull() ? null :
                           timeTrackingNode.path("timeSpentSeconds").asLong();
            }

            return Optional.of(Issue.create(
                    issueId,
                    issueKey,
                    boardId,
                    sprintId,
                    issueType,
                    status,
                    priority,
                    assigneeAccountId,
                    assigneeDisplayName,
                    reporterAccountId,
                    reporterDisplayName,
                    summary,
                    description,
                    storyPoints,
                    originalEstimate,
                    remainingEstimate,
                    timeSpent,
                    createdDate,
                    updatedDate,
                    resolvedDate,
                    dueDate,
                    labels,
                    components,
                    fixVersions
            ));

        } catch (Exception e) {
            logger.error("Error extracting issue from node: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts sprint ID from issue fields.
     *
     * @param fields The issue fields JSON node
     * @return Sprint ID if found, null otherwise
     */
    private Long extractSprintIdFromIssue(JsonNode fields) {
        try {
            // Check common sprint fields
            JsonNode sprintField = fields.path("customfield_10020"); // Common sprint field
            if (sprintField.isMissingNode()) {
                sprintField = fields.path("sprint");
            }

            if (!sprintField.isMissingNode() && sprintField.isArray() && !sprintField.isEmpty()) {
                JsonNode firstSprint = sprintField.get(0);
                if (firstSprint.isObject()) {
                    return firstSprint.path("id").isNull() ? null : firstSprint.path("id").asLong();
                }
            }

            return null;
        } catch (Exception e) {
            logger.debug("Could not extract sprint ID from issue fields: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parses a date-time string into LocalDateTime.
     *
     * @param dateTimeString The date-time string
     * @return LocalDateTime or null if parsing fails
     */
    private LocalDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }

        try {
            // Handle ISO 8601 format with timezone
            if (dateTimeString.contains("T")) {
                return LocalDateTime.parse(dateTimeString.substring(0, 19));
            }
            return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            logger.debug("Could not parse date-time string: {}", dateTimeString);
            return null;
        }
    }

    /**
     * Retrieves all JIRA data records for a specific board.
     *
     * @param boardId The board ID
     * @return List of JIRA data records for the board
     */
    public List<JiraData> getAllBoardData(Long boardId) {
        if (boardId == null) {
            logger.warn("Board ID is null in getAllBoardData");
            return List.of();
        }
        // Fetch all JiraData for the board, regardless of data type
        return StreamSupport.stream(jiraDataRepository.findAll().spliterator(), false)
                .filter(data -> boardId.equals(data.getBoardId()))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the latest JIRA data record for a specific board and data type.
     *
     * @param boardId  The board ID
     * @param dataType The data type (e.g., issues, sprints, board_config)
     * @return Optional containing the latest JIRA data record if found, otherwise empty
     */
    public Optional<JiraData> getLatestBoardData(Long boardId, String dataType) {
        if (boardId == null || dataType == null || dataType.isBlank()) {
            logger.warn("Invalid boardId or dataType in getLatestBoardData");
            return Optional.empty();
        }
        JiraData latest = jiraDataRepository.findLatestByBoardIdAndDataType(boardId, dataType);
        return Optional.ofNullable(latest);
    }

    /**
     * Retrieves all sprints for a specific board.
     * This method is used for diagnostics and comprehensive metrics calculation.
     *
     * @param boardId The board ID
     * @return List of all sprints for the board
     */
    public List<Sprint> getAllSprintsForBoard(Long boardId) {
        logger.debug("Retrieving all sprints for board ID: {}", boardId);
        return sprintRepository.findByBoardId(boardId);
    }

    /**
     * Checks if a sprint has calculated metrics.
     *
     * @param boardId The board ID
     * @param sprintId The sprint ID
     * @return true if metrics exist for the sprint, false otherwise
     */
    public boolean sprintHasMetrics(Long boardId, Long sprintId) {
        try {
            return boardMetricsRepository.findByBoardIdAndSprintId(boardId, sprintId).isPresent();
        } catch (Exception e) {
            logger.debug("Error checking metrics for sprint {}: {}", sprintId, e.getMessage());
            return false;
        }
    }
}

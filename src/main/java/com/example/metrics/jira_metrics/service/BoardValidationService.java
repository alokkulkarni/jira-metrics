package com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.entity.Board;
import com.example.metrics.jira_metrics.entity.BoardMetrics;
import com.example.metrics.jira_metrics.entity.Issue;
import com.example.metrics.jira_metrics.repository.BoardRepository;
import com.example.metrics.jira_metrics.repository.IssueRepository;
import com.example.metrics.jira_metrics.repository.SprintRepository;
import com.example.metrics.jira_metrics.repository.BoardMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for validating boards and determining appropriate metrics calculation strategy.
 * Handles both sprint-based boards (Scrum) and issue-based boards (Kanban/Simple).
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Service
@Transactional
public class BoardValidationService {

    private static final Logger logger = LoggerFactory.getLogger(BoardValidationService.class);

    private final BoardRepository boardRepository;
    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;
    private final BoardMetricsRepository boardMetricsRepository;
    private final JiraClientService jiraClientService;

    /**
     * Constructor for BoardValidationService.
     *
     * @param boardRepository Repository for board operations
     * @param sprintRepository Repository for sprint operations
     * @param issueRepository Repository for issue operations
     * @param boardMetricsRepository Repository for metrics operations
     * @param jiraClientService Service for JIRA API calls
     */
    public BoardValidationService(BoardRepository boardRepository,
                                SprintRepository sprintRepository,
                                IssueRepository issueRepository,
                                BoardMetricsRepository boardMetricsRepository,
                                JiraClientService jiraClientService) {
        this.boardRepository = boardRepository;
        this.sprintRepository = sprintRepository;
        this.issueRepository = issueRepository;
        this.boardMetricsRepository = boardMetricsRepository;
        this.jiraClientService = jiraClientService;
    }

    /**
     * Validates a board and updates its sprint availability information.
     * Determines if the board has sprints or only issues in backlog.
     *
     * @param boardId The board ID to validate
     * @return Updated board with sprint information
     */
    public Optional<Board> validateAndUpdateBoard(Long boardId) {
        logger.info("Validating board ID: {}", boardId);

        var boardOpt = boardRepository.findByBoardId(boardId);
        if (boardOpt.isEmpty()) {
            logger.warn("Board not found for ID: {}", boardId);
            return Optional.empty();
        }

        var board = boardOpt.get();

        try {
            // Check for sprints associated with this board
            var sprints = sprintRepository.findByBoardId(boardId);
            var sprintCount = sprints.size();
            var hasSprints = sprintCount > 0;

            // Get board type from JIRA API if not already set
            if (board.getBoardType() == null) {
                var boardType = fetchBoardTypeFromJira(boardId);
                board.setBoardType(boardType);
            }

            // Update board with sprint information
            board.setHasSprints(hasSprints);
            board.setSprintCount(sprintCount);
            board.setUpdatedAt(LocalDateTime.now());

            var updatedBoard = boardRepository.save(board);

            logger.info("Board {} validated - Type: {}, Has Sprints: {}, Sprint Count: {}",
                       boardId, board.getBoardType(), hasSprints, sprintCount);

            return Optional.of(updatedBoard);

        } catch (Exception e) {
            logger.error("Error validating board {}: {}", boardId, e.getMessage(), e);
            return Optional.of(board); // Return original board if validation fails
        }
    }

    /**
     * Calculates appropriate metrics for a board based on its type and sprint availability.
     *
     * @param boardId The board ID to calculate metrics for
     * @param periodStart Optional start date for metrics calculation period
     * @param periodEnd Optional end date for metrics calculation period
     * @return Calculated metrics or empty if calculation fails
     */
    public Optional<BoardMetrics> calculateBoardMetrics(Long boardId,
                                                       LocalDateTime periodStart,
                                                       LocalDateTime periodEnd) {
        logger.info("Calculating metrics for board ID: {}", boardId);

        var boardOpt = validateAndUpdateBoard(boardId);
        if (boardOpt.isEmpty()) {
            return Optional.empty();
        }

        var board = boardOpt.get();

        if (board.supportsSprintMetrics()) {
            return calculateSprintBasedMetrics(board, periodStart, periodEnd);
        } else {
            return calculateIssueBasedMetrics(board, periodStart, periodEnd);
        }
    }

    /**
     * Calculates sprint-based metrics for boards with sprints.
     *
     * @param board The board to calculate metrics for
     * @param periodStart Start date for calculation period
     * @param periodEnd End date for calculation period
     * @return Sprint-based metrics
     */
    private Optional<BoardMetrics> calculateSprintBasedMetrics(Board board,
                                                             LocalDateTime periodStart,
                                                             LocalDateTime periodEnd) {
        logger.info("Calculating sprint-based metrics for board {}", board.getBoardId());

        // Get the most recent completed sprint or active sprint
        var sprints = sprintRepository.findByBoardId(board.getBoardId());
        if (sprints.isEmpty()) {
            logger.warn("No sprints found for board {}", board.getBoardId());
            return Optional.empty();
        }

        // Use the most recent sprint for metrics calculation
        var latestSprint = sprints.stream()
                .max((s1, s2) -> s1.startDate().compareTo(s2.startDate()))
                .get();

        var sprintStart = latestSprint.startDate();
        var sprintEnd = latestSprint.endDate();

        // Override with provided period if available
        var metricsStart = periodStart != null ? periodStart : sprintStart;
        var metricsEnd = periodEnd != null ? periodEnd : sprintEnd;

        var issues = issueRepository.findBySprintId(latestSprint.sprintId());

        var metrics = BoardMetrics.createSprintBased(
            board.getBoardId(),
            latestSprint.sprintId(),
            metricsStart,
            metricsEnd,
            board.getBoardType()
        );

        return Optional.of(enhanceWithSprintMetrics(metrics, issues));
    }

    /**
     * Calculates issue-based metrics for boards without sprints.
     *
     * @param board The board to calculate metrics for
     * @param periodStart Start date for calculation period
     * @param periodEnd End date for calculation period
     * @return Issue-based metrics
     */
    private Optional<BoardMetrics> calculateIssueBasedMetrics(Board board,
                                                            LocalDateTime periodStart,
                                                            LocalDateTime periodEnd) {
        logger.info("Calculating issue-based metrics for board {}", board.getBoardId());

        // Default to last 30 days if no period specified
        var metricsEnd = periodEnd != null ? periodEnd : LocalDateTime.now();
        var metricsStart = periodStart != null ? periodStart : metricsEnd.minusDays(30);

        var issues = issueRepository.findByBoardId(board.getBoardId());

        if (issues.isEmpty()) {
            logger.warn("No issues found for board {}", board.getBoardId());
            return Optional.empty();
        }

        var metrics = BoardMetrics.createIssueBased(
            board.getBoardId(),
            metricsStart,
            metricsEnd,
            board.getBoardType()
        );

        return Optional.of(enhanceWithIssueMetrics(metrics, issues, metricsStart, metricsEnd));
    }

    /**
     * Enhances metrics with sprint-specific calculations.
     */
    private BoardMetrics enhanceWithSprintMetrics(BoardMetrics baseMetrics, List<Issue> issues) {
        var completedIssues = issues.stream()
                .filter(issue -> "Done".equalsIgnoreCase(issue.status()) ||
                               "Closed".equalsIgnoreCase(issue.status()) ||
                               "Resolved".equalsIgnoreCase(issue.status()))
                .collect(Collectors.toList());

        var totalStoryPoints = issues.stream()
                .map(Issue::storyPoints)
                .filter(sp -> sp != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var completedStoryPoints = completedIssues.stream()
                .map(Issue::storyPoints)
                .filter(sp -> sp != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var cycleTimeAvg = calculateAverageCycleTime(completedIssues);
        var defectCount = (int) issues.stream()
                .filter(issue -> "Bug".equalsIgnoreCase(issue.issueType()))
                .count();

        return new BoardMetrics(
                baseMetrics.id(),
                baseMetrics.boardId(),
                baseMetrics.sprintId(),
                baseMetrics.metricPeriodStart(),
                baseMetrics.metricPeriodEnd(),
                baseMetrics.metricType(),
                baseMetrics.boardType(),
                // Velocity metrics
                completedStoryPoints,
                completedIssues.size(),
                totalStoryPoints,
                completedStoryPoints,
                // Quality metrics
                defectCount,
                calculateDefectRate(defectCount, issues.size()),
                0, // escaped defects - would need additional data
                BigDecimal.ZERO, // defect density
                // Flow metrics
                cycleTimeAvg,
                calculateMedianCycleTime(completedIssues),
                cycleTimeAvg, // lead time approximation
                calculateMedianCycleTime(completedIssues),
                // Churn metrics (sprint-specific)
                0, // scope changes - would need sprint history
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                // Predictability
                calculateCommitmentReliability(totalStoryPoints, completedStoryPoints),
                completedStoryPoints.compareTo(totalStoryPoints.multiply(BigDecimal.valueOf(0.8))) >= 0,
                // Throughput
                completedIssues.size(),
                completedStoryPoints,
                // Team metrics
                BigDecimal.ZERO, // would need capacity data
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                // Issue-based metrics (not applicable)
                null, null, null, null,
                baseMetrics.createdAt()
        );
    }

    /**
     * Enhances metrics with issue-specific calculations.
     */
    private BoardMetrics enhanceWithIssueMetrics(BoardMetrics baseMetrics, List<Issue> issues,
                                                LocalDateTime start, LocalDateTime end) {
        // Filter issues by the specified period
        var periodIssues = issues.stream()
                .filter(issue -> isIssueInPeriod(issue, start, end))
                .collect(Collectors.toList());

        var statusGroups = periodIssues.stream()
                .collect(Collectors.groupingBy(Issue::status));

        var doneIssues = statusGroups.getOrDefault("Done", List.of()).size() +
                        statusGroups.getOrDefault("Closed", List.of()).size() +
                        statusGroups.getOrDefault("Resolved", List.of()).size();

        var inProgressIssues = statusGroups.getOrDefault("In Progress", List.of()).size() +
                              statusGroups.getOrDefault("In Development", List.of()).size();

        var backlogIssues = statusGroups.getOrDefault("To Do", List.of()).size() +
                           statusGroups.getOrDefault("Backlog", List.of()).size() +
                           statusGroups.getOrDefault("Open", List.of()).size();

        var completedInPeriod = periodIssues.stream()
                .filter(issue -> "Done".equalsIgnoreCase(issue.status()) ||
                               "Closed".equalsIgnoreCase(issue.status()) ||
                               "Resolved".equalsIgnoreCase(issue.status()))
                .collect(Collectors.toList());

        var storyPointsCompleted = completedInPeriod.stream()
                .map(Issue::storyPoints)
                .filter(sp -> sp != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var cycleTimeAvg = calculateAverageCycleTime(completedInPeriod);
        var defectCount = (int) periodIssues.stream()
                .filter(issue -> "Bug".equalsIgnoreCase(issue.issueType()))
                .count();

        return new BoardMetrics(
                baseMetrics.id(),
                baseMetrics.boardId(),
                null, // no sprint ID for issue-based
                baseMetrics.metricPeriodStart(),
                baseMetrics.metricPeriodEnd(),
                baseMetrics.metricType(),
                baseMetrics.boardType(),
                // Velocity metrics
                storyPointsCompleted,
                completedInPeriod.size(),
                null, // no planned story points for issue-based
                storyPointsCompleted,
                // Quality metrics
                defectCount,
                calculateDefectRate(defectCount, periodIssues.size()),
                0,
                BigDecimal.ZERO,
                // Flow metrics
                cycleTimeAvg,
                calculateMedianCycleTime(completedInPeriod),
                cycleTimeAvg,
                calculateMedianCycleTime(completedInPeriod),
                // Churn metrics (not applicable for issue-based)
                null, null, null, null,
                // Predictability (modified for issue-based)
                BigDecimal.valueOf(0.8), // default reliability
                doneIssues > 0,
                // Throughput
                completedInPeriod.size(),
                storyPointsCompleted,
                // Team metrics
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                // Issue-based specific metrics
                inProgressIssues,
                backlogIssues,
                doneIssues,
                BigDecimal.valueOf(1.0), // WIP adherence placeholder
                baseMetrics.createdAt()
        );
    }

    /**
     * Fetches board type from JIRA API.
     */
    private String fetchBoardTypeFromJira(Long boardId) {
        try {
            // This would make an actual API call to get board details
            // For now, returning a default based on sprint availability
            var sprints = sprintRepository.findByBoardId(boardId);
            return sprints.isEmpty() ? "kanban" : "scrum";
        } catch (Exception e) {
            logger.warn("Could not fetch board type for {}: {}", boardId, e.getMessage());
            return "simple";
        }
    }

    /**
     * Helper methods for metric calculations
     */
    private boolean isIssueInPeriod(Issue issue, LocalDateTime start, LocalDateTime end) {
        var created = issue.createdDate();
        var updated = issue.updatedDate();
        return (created != null && created.isAfter(start) && created.isBefore(end)) ||
               (updated != null && updated.isAfter(start) && updated.isBefore(end));
    }

    private BigDecimal calculateAverageCycleTime(List<Issue> issues) {
        var cycleTimesInDays = issues.stream()
                .filter(issue -> issue.createdDate() != null && issue.updatedDate() != null)
                .mapToLong(issue -> ChronoUnit.DAYS.between(issue.createdDate(), issue.updatedDate()))
                .filter(days -> days > 0)
                .boxed()
                .collect(Collectors.toList());

        if (cycleTimesInDays.isEmpty()) {
            return BigDecimal.ZERO;
        }

        var average = cycleTimesInDays.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        return BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMedianCycleTime(List<Issue> issues) {
        var cycleTimesInDays = issues.stream()
                .filter(issue -> issue.createdDate() != null && issue.updatedDate() != null)
                .mapToLong(issue -> ChronoUnit.DAYS.between(issue.createdDate(), issue.updatedDate()))
                .filter(days -> days > 0)
                .sorted()
                .boxed()
                .collect(Collectors.toList());

        if (cycleTimesInDays.isEmpty()) {
            return BigDecimal.ZERO;
        }

        var size = cycleTimesInDays.size();
        if (size % 2 == 0) {
            var median = (cycleTimesInDays.get(size / 2 - 1) + cycleTimesInDays.get(size / 2)) / 2.0;
            return BigDecimal.valueOf(median).setScale(2, RoundingMode.HALF_UP);
        } else {
            return BigDecimal.valueOf(cycleTimesInDays.get(size / 2)).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal calculateDefectRate(int defectCount, int totalIssues) {
        if (totalIssues == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(defectCount)
                .divide(BigDecimal.valueOf(totalIssues), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateCommitmentReliability(BigDecimal planned, BigDecimal completed) {
        if (planned == null || planned.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return completed.divide(planned, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}

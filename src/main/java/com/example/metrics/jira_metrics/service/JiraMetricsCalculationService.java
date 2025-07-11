package com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.entity.BoardMetrics;
import com.example.metrics.jira_metrics.entity.Issue;
import com.example.metrics.jira_metrics.repository.BoardMetricsRepository;
import com.example.metrics.jira_metrics.repository.IssueRepository;
import com.example.metrics.jira_metrics.repository.SprintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for calculating comprehensive JIRA metrics and insights.
 * Computes velocity, quality, flow, churn, and predictability metrics.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Service
@Transactional
public class JiraMetricsCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(JiraMetricsCalculationService.class);

    private final IssueRepository issueRepository;
    private final SprintRepository sprintRepository;
    private final BoardMetricsRepository boardMetricsRepository;

    /**
     * Constructs the metrics calculation service with required dependencies.
     *
     * @param issueRepository Repository for issue operations
     * @param sprintRepository Repository for sprint operations
     * @param boardMetricsRepository Repository for metrics operations
     */
    public JiraMetricsCalculationService(IssueRepository issueRepository,
                                       SprintRepository sprintRepository,
                                       BoardMetricsRepository boardMetricsRepository) {
        this.issueRepository = issueRepository;
        this.sprintRepository = sprintRepository;
        this.boardMetricsRepository = boardMetricsRepository;
    }

    /**
     * Calculates comprehensive metrics for any sprint (active or completed).
     * For active sprints, provides current progress metrics.
     * For completed sprints, provides final performance metrics.
     *
     * @param sprintId The sprint ID to calculate metrics for
     * @return Optional containing calculated metrics, empty if sprint not found
     */
    public Optional<BoardMetrics> calculateSprintMetrics(Long sprintId) {
        logger.info("Calculating metrics for sprint ID: {}", sprintId);

        try {
            var sprintOpt = sprintRepository.findBySprintId(sprintId);
            if (sprintOpt.isEmpty()) {
                logger.warn("Sprint not found for ID: {}", sprintId);
                return Optional.empty();
            }

            var sprint = sprintOpt.get();
            var isCompleted = sprint.isCompleted();

            logger.info("Sprint {} status: {} - calculating {} metrics",
                       sprintId, sprint.sprintState(), isCompleted ? "final" : "progress");

            var issues = issueRepository.findBySprintId(sprintId);
            logger.info("Found {} issues for sprint {}", issues.size(), sprintId);

            if (issues.isEmpty()) {
                logger.warn("No issues found for sprint {}, skipping metrics calculation", sprintId);
                return Optional.empty();
            }

            // Check if metrics already exist for this sprint
            var existingMetrics = boardMetricsRepository.findByBoardIdAndSprintId(sprint.boardId(), sprintId);
            if (existingMetrics.isPresent() && isCompleted) {
                logger.info("Final metrics already exist for completed sprint {}, returning existing metrics", sprintId);
                return existingMetrics;
            }

            // For active sprints, always recalculate to get latest progress
            if (existingMetrics.isPresent() && !isCompleted) {
                logger.info("Recalculating progress metrics for active sprint {}", sprintId);
            }

            // Create comprehensive metrics with all calculations
            var metricsEndDate = isCompleted && sprint.completeDate() != null
                               ? sprint.completeDate()
                               : sprint.endDate();

            var calculatedMetrics = createComprehensiveMetrics(
                sprint.boardId(),
                sprintId,
                sprint.startDate(),
                metricsEndDate,
                issues,
                isCompleted
            );

            // Save calculated metrics
            var savedMetrics = boardMetricsRepository.save(calculatedMetrics);

            logger.info("Successfully calculated and saved {} metrics for sprint {} with ID: {}",
                       isCompleted ? "final" : "progress", sprintId, savedMetrics.id());

            return Optional.of(savedMetrics);

        } catch (Exception e) {
            logger.error("Error calculating metrics for sprint {}: {}", sprintId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Creates comprehensive BoardMetrics with all calculated values.
     *
     * @param boardId The board ID
     * @param sprintId The sprint ID
     * @param periodStart The metrics period start
     * @param periodEnd The metrics period end
     * @param issues The list of issues for calculation
     * @param isCompleted Whether the sprint is completed
     * @return Complete BoardMetrics instance
     */
    private BoardMetrics createComprehensiveMetrics(Long boardId, Long sprintId,
                                                   LocalDateTime periodStart, LocalDateTime periodEnd,
                                                   List<Issue> issues, boolean isCompleted) {

        // Calculate velocity metrics
        var totalStoryPoints = issues.stream()
                .map(Issue::storyPoints)
                .filter(sp -> sp != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var completedIssues = issues.stream()
                .filter(this::isIssueCompleted)
                .collect(Collectors.toList());

        var completedStoryPoints = completedIssues.stream()
                .map(Issue::storyPoints)
                .filter(sp -> sp != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var completedIssueCount = completedIssues.size();

        // Calculate quality metrics
        var defectCount = (int) issues.stream()
                .filter(issue -> "Bug".equalsIgnoreCase(issue.issueType()))
                .count();

        var defectRate = issues.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(defectCount)
                        .divide(BigDecimal.valueOf(issues.size()), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        // Calculate flow metrics
        var cycleTimeAvg = calculateAverageCycleTime(completedIssues);
        var cycleTimeMedian = calculateMedianCycleTime(completedIssues);

        // Calculate predictability metrics
        var commitmentReliability = totalStoryPoints.compareTo(BigDecimal.ZERO) > 0 ?
                completedStoryPoints.divide(totalStoryPoints, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;

        var sprintGoalSuccess = commitmentReliability.compareTo(BigDecimal.valueOf(80)) >= 0;

        logger.debug("Calculated metrics - Velocity: {} SP completed, Quality: {} defects, " +
                    "Flow: {} avg cycle time, Predictability: {}% commitment reliability",
                    completedStoryPoints, defectCount, cycleTimeAvg, commitmentReliability);

        return new BoardMetrics(
                null, // id will be generated
                boardId,
                sprintId,
                periodStart,
                periodEnd,
                BoardMetrics.MetricType.SPRINT_BASED.getValue(),
                "scrum", // Sprint-based boards are typically Scrum
                // Velocity metrics
                completedStoryPoints,
                completedIssueCount,
                totalStoryPoints,
                completedStoryPoints,
                // Quality metrics
                defectCount,
                defectRate,
                0, // escaped defects - would need additional data
                BigDecimal.ZERO, // defect density
                // Flow metrics
                cycleTimeAvg,
                cycleTimeMedian,
                cycleTimeAvg, // lead time approximation
                cycleTimeMedian,
                // Churn metrics (sprint-specific)
                0, // scope changes - would need sprint history
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                // Predictability metrics
                commitmentReliability,
                sprintGoalSuccess,
                // Throughput metrics
                completedIssueCount,
                completedStoryPoints,
                // Team metrics
                BigDecimal.ZERO, // would need capacity data
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                // Issue-based metrics (not applicable for sprint-based)
                null, null, null, null,
                LocalDateTime.now()
        );
    }

    /**
     * Checks if an issue is completed.
     *
     * @param issue The issue to check
     * @return true if the issue is completed
     */
    private boolean isIssueCompleted(Issue issue) {
        var status = issue.status();
        return "Done".equalsIgnoreCase(status) ||
               "Closed".equalsIgnoreCase(status) ||
               "Resolved".equalsIgnoreCase(status);
    }

    /**
     * Calculates average cycle time for completed issues.
     *
     * @param completedIssues List of completed issues
     * @return Average cycle time in days
     */
    private BigDecimal calculateAverageCycleTime(List<Issue> completedIssues) {
        var cycleTimesInDays = completedIssues.stream()
                .filter(issue -> issue.createdDate() != null && issue.resolvedDate() != null)
                .mapToLong(issue -> ChronoUnit.DAYS.between(issue.createdDate(), issue.resolvedDate()))
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

    /**
     * Calculates median cycle time for completed issues.
     *
     * @param completedIssues List of completed issues
     * @return Median cycle time in days
     */
    private BigDecimal calculateMedianCycleTime(List<Issue> completedIssues) {
        var cycleTimesInDays = completedIssues.stream()
                .filter(issue -> issue.createdDate() != null && issue.resolvedDate() != null)
                .mapToLong(issue -> ChronoUnit.DAYS.between(issue.createdDate(), issue.resolvedDate()))
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

    /**
     * Calculates board-level metrics across multiple sprints.
     *
     * @param boardId     The board ID
     * @param sprintCount Number of recent sprints to analyze
     */
    public void calculateBoardMetrics(Long boardId, int sprintCount) {
        logger.info("Calculating board metrics for board {} using {} recent sprints",
                   boardId, sprintCount);

        try {
            var recentSprints = sprintRepository.findRecentCompletedSprints(boardId, sprintCount);
            if (recentSprints.isEmpty()) {
                logger.warn("No completed sprints found for board {}", boardId);
                return;
            }

            logger.info("Found {} completed sprints for board {}", recentSprints.size(), boardId);

            // Calculate metrics for each sprint if not already calculated
            int metricsCalculated = 0;
            for (var sprint : recentSprints) {
                var existingMetrics = boardMetricsRepository.findByBoardIdAndSprintId(
                        boardId, sprint.sprintId());
                if (existingMetrics.isEmpty()) {
                    var calculated = calculateSprintMetrics(sprint.sprintId());
                    if (calculated.isPresent()) {
                        metricsCalculated++;
                    }
                } else {
                    logger.debug("Metrics already exist for sprint {}", sprint.sprintId());
                }
            }

            logger.info("Calculated metrics for {} new sprints on board {}", metricsCalculated, boardId);

            // Try to get average metrics across sprints (these methods might not exist yet)
            try {
                var avgVelocity = boardMetricsRepository.getAverageVelocity(boardId, sprintCount);
                var avgDefectRate = boardMetricsRepository.getAverageDefectRate(boardId, sprintCount);
                var avgCycleTime = boardMetricsRepository.getAverageCycleTime(boardId, sprintCount);

                logger.info("Board {} averages: velocity={}, defect rate={}, cycle time={}",
                           boardId, avgVelocity, avgDefectRate, avgCycleTime);
            } catch (Exception e) {
                logger.debug("Average metrics methods not implemented yet: {}", e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error calculating board metrics for board {}: {}", boardId, e.getMessage(), e);
        }
    }

    /**
     * Calculates the median value from a list of doubles.
     *
     * @param sortedValues List of sorted double values
     * @return Median value
     */
    private double calculateMedian(List<Double> sortedValues) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }

        int size = sortedValues.size();
        if (size % 2 == 0) {
            return (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
        } else {
            return sortedValues.get(size / 2);
        }
    }
}

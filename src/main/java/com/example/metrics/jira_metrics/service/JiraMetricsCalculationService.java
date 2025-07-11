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

            // Create base metrics with appropriate end date
            var metricsEndDate = isCompleted && sprint.completeDate() != null
                               ? sprint.completeDate()
                               : sprint.endDate();

            var metrics = BoardMetrics.create(sprint.boardId(), sprint.sprintId(),
                                            sprint.startDate(), metricsEndDate);

            // Calculate and build metrics step by step - IMPORTANT: use returned instances
            metrics = calculateVelocityMetrics(metrics, issues, isCompleted);
            metrics = calculateQualityMetrics(metrics, issues, isCompleted);
            metrics = calculateFlowMetrics(metrics, issues, isCompleted);
            metrics = calculateChurnMetrics(metrics, issues, isCompleted);

            // Save calculated metrics (update existing if present)
            var savedMetrics = existingMetrics.isPresent()
                             ? boardMetricsRepository.save(metrics.withId(existingMetrics.get().id()))
                             : boardMetricsRepository.save(metrics);

            logger.info("Successfully calculated and saved {} metrics for sprint {} with ID: {}",
                       isCompleted ? "final" : "progress", sprintId, savedMetrics.id());

            return Optional.of(savedMetrics);

        } catch (Exception e) {
            logger.error("Error calculating metrics for sprint {}: {}", sprintId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Calculates velocity metrics including story points and issue count.
     * Handles both active and completed sprints appropriately.
     *
     * @param metrics The base metrics object
     * @param issues List of issues in the sprint
     * @param isCompleted Whether the sprint is completed
     * @return Updated metrics with velocity data
     */
    private BoardMetrics calculateVelocityMetrics(BoardMetrics metrics, List<Issue> issues, boolean isCompleted) {
        logger.debug("Calculating {} velocity metrics for {} issues",
                    isCompleted ? "final" : "progress", issues.size());

        var totalStoryPoints = issues.stream()
                .mapToDouble(Issue::getStoryPointsAsDouble)
                .sum();

        var completedStoryPoints = issues.stream()
                .filter(Issue::isResolved)
                .mapToDouble(Issue::getStoryPointsAsDouble)
                .sum();

        var completedIssueCount = (int) issues.stream()
                .filter(Issue::isResolved)
                .count();

        // For active sprints, log current progress
        if (!isCompleted) {
            var progressPercentage = totalStoryPoints > 0
                ? (completedStoryPoints / totalStoryPoints) * 100
                : 0.0;
            logger.debug("Sprint progress: {:.1f}% complete ({} of {} story points)",
                        progressPercentage, completedStoryPoints, totalStoryPoints);
        }

        logger.debug("Velocity: {} completed story points, {} completed issues",
                    completedStoryPoints, completedIssueCount);

        return metrics.withVelocityMetrics(
                metrics.boardId(),
                metrics.sprintId(),
                metrics.metricPeriodStart(),
                metrics.metricPeriodEnd(),
                BigDecimal.valueOf(completedStoryPoints),
                completedIssueCount,
                BigDecimal.valueOf(totalStoryPoints),
                BigDecimal.valueOf(completedStoryPoints)
        );
    }

    /**
     * Calculates quality metrics including defect rate and density.
     * Adapts calculations based on sprint completion status.
     *
     * @param metrics The base metrics object
     * @param issues List of issues in the sprint
     * @param isCompleted Whether the sprint is completed
     * @return Updated metrics with quality data
     */
    private BoardMetrics calculateQualityMetrics(BoardMetrics metrics, List<Issue> issues, boolean isCompleted) {
        logger.debug("Calculating {} quality metrics", isCompleted ? "final" : "current");

        var defectCount = (int) issues.stream()
                .filter(Issue::isDefect)
                .count();

        var totalIssues = issues.size();
        var defectRate = totalIssues > 0 ?
                BigDecimal.valueOf((double) defectCount / totalIssues)
                        .setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        var totalStoryPoints = issues.stream()
                .mapToDouble(Issue::getStoryPointsAsDouble)
                .sum();

        var defectDensity = totalStoryPoints > 0 ?
                BigDecimal.valueOf(defectCount / totalStoryPoints)
                        .setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // For escaped defects, only count for completed sprints or use current time for active sprints
        var cutoffDate = isCompleted ? metrics.metricPeriodEnd() : LocalDateTime.now();
        var escapedDefects = (int) issues.stream()
                .filter(Issue::isDefect)
                .filter(issue -> issue.resolvedDate() != null &&
                               issue.resolvedDate().isAfter(cutoffDate))
                .count();

        logger.debug("Quality: {} defects, rate: {}, density: {} ({})",
                    defectCount, defectRate, defectDensity,
                    isCompleted ? "final" : "current");

        return metrics.withQualityMetrics(defectCount, defectRate, escapedDefects, defectDensity);
    }

    /**
     * Calculates flow metrics including cycle time and lead time.
     * Provides meaningful metrics for both active and completed sprints.
     *
     * @param metrics The base metrics object
     * @param issues List of issues in the sprint
     * @param isCompleted Whether the sprint is completed
     * @return Updated metrics with flow data
     */
    private BoardMetrics calculateFlowMetrics(BoardMetrics metrics, List<Issue> issues, boolean isCompleted) {
        logger.debug("Calculating {} flow metrics", isCompleted ? "final" : "current");

        var cycleTimes = issues.stream()
                .filter(Issue::isResolved)
                .map(Issue::getCycleTimeHours)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        if (cycleTimes.isEmpty()) {
            logger.debug("No completed issues with cycle time data found for {} sprint",
                        isCompleted ? "completed" : "active");
            return metrics;
        }

        var avgCycleTime = cycleTimes.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        var sortedCycleTimes = cycleTimes.stream()
                .sorted()
                .collect(Collectors.toList());

        var medianCycleTime = calculateMedian(sortedCycleTimes);

        // For simplicity, using cycle time as lead time (can be enhanced)
        var avgLeadTime = avgCycleTime;
        var medianLeadTime = medianCycleTime;

        logger.debug("Flow: avg cycle time: {} hours, median: {} hours (based on {} completed issues)",
                    avgCycleTime, medianCycleTime, cycleTimes.size());

        return metrics.withFlowMetrics(
                BigDecimal.valueOf(avgCycleTime).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(medianCycleTime).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(avgLeadTime).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(medianLeadTime).setScale(2, RoundingMode.HALF_UP)
        );
    }

    /**
     * Calculates churn metrics including scope changes.
     * Tracks scope changes throughout the sprint lifecycle.
     *
     * @param metrics The base metrics object
     * @param issues List of issues in the sprint
     * @param isCompleted Whether the sprint is completed
     * @return Updated metrics with churn data
     */
    private BoardMetrics calculateChurnMetrics(BoardMetrics metrics, List<Issue> issues, boolean isCompleted) {
        logger.debug("Calculating {} churn metrics", isCompleted ? "final" : "current");

        var totalStoryPoints = issues.stream()
                .mapToDouble(Issue::getStoryPointsAsDouble)
                .sum();

        // Estimate scope changes based on issue creation dates during sprint
        var scopeChanges = (int) issues.stream()
                .filter(issue -> issue.createdDate() != null &&
                               issue.createdDate().isAfter(metrics.metricPeriodStart()))
                .count();

        var addedStoryPoints = issues.stream()
                .filter(issue -> issue.createdDate() != null &&
                               issue.createdDate().isAfter(metrics.metricPeriodStart()))
                .mapToDouble(Issue::getStoryPointsAsDouble)
                .sum();

        var churnRate = totalStoryPoints > 0 ?
                BigDecimal.valueOf(addedStoryPoints / totalStoryPoints)
                        .setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // Log additional context for active sprints
        if (!isCompleted && scopeChanges > 0) {
            logger.info("Active sprint {} has {} scope changes ({} story points added)",
                       metrics.sprintId(), scopeChanges, addedStoryPoints);
        }

        logger.debug("Churn: {} scope changes, rate: {}, added points: {}",
                    scopeChanges, churnRate, addedStoryPoints);

        return metrics.withChurnMetrics(
                scopeChanges,
                churnRate,
                BigDecimal.valueOf(addedStoryPoints),
                BigDecimal.ZERO // Simplified - not tracking removed points
        );
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

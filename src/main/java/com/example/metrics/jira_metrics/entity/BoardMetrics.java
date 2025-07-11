package com.example.metrics.jira_metrics.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing calculated JIRA metrics and insights for a board and time period.
 * Includes velocity, quality, flow, churn, predictability, and team metrics.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Table("board_metrics")
public record BoardMetrics(
        @Id
        Long id,

        @Column("board_id")
        Long boardId,

        @Column("sprint_id")
        Long sprintId,

        @Column("metric_period_start")
        LocalDateTime metricPeriodStart,

        @Column("metric_period_end")
        LocalDateTime metricPeriodEnd,

        // Velocity metrics
        @Column("velocity_story_points")
        BigDecimal velocityStoryPoints,

        @Column("velocity_issue_count")
        Integer velocityIssueCount,

        @Column("planned_story_points")
        BigDecimal plannedStoryPoints,

        @Column("completed_story_points")
        BigDecimal completedStoryPoints,

        // Quality metrics
        @Column("defect_count")
        Integer defectCount,

        @Column("defect_rate")
        BigDecimal defectRate,

        @Column("escaped_defects")
        Integer escapedDefects,

        @Column("defect_density")
        BigDecimal defectDensity,

        // Flow metrics
        @Column("cycle_time_avg")
        BigDecimal cycleTimeAvg,

        @Column("cycle_time_median")
        BigDecimal cycleTimeMedian,

        @Column("lead_time_avg")
        BigDecimal leadTimeAvg,

        @Column("lead_time_median")
        BigDecimal leadTimeMedian,

        // Churn metrics
        @Column("scope_change_count")
        Integer scopeChangeCount,

        @Column("scope_churn_rate")
        BigDecimal scopeChurnRate,

        @Column("added_story_points")
        BigDecimal addedStoryPoints,

        @Column("removed_story_points")
        BigDecimal removedStoryPoints,

        // Predictability metrics
        @Column("commitment_reliability")
        BigDecimal commitmentReliability,

        @Column("sprint_goal_success")
        Boolean sprintGoalSuccess,

        // Throughput metrics
        @Column("throughput_issues")
        Integer throughputIssues,

        @Column("throughput_story_points")
        BigDecimal throughputStoryPoints,

        // Team metrics
        @Column("team_capacity_hours")
        BigDecimal teamCapacityHours,

        @Column("utilization_rate")
        BigDecimal utilizationRate,

        @Column("created_at")
        LocalDateTime createdAt,

        @Column("updated_at")
        LocalDateTime updatedAt
) {

    /**
     * Creates a new BoardMetrics instance with current timestamp.
     *
     * @param boardId The board ID
     * @param sprintId The sprint ID (optional)
     * @param metricPeriodStart Start of the metrics period
     * @param metricPeriodEnd End of the metrics period
     * @return A new BoardMetrics instance with default values
     */
    public static BoardMetrics create(Long boardId, Long sprintId,
                                    LocalDateTime metricPeriodStart, LocalDateTime metricPeriodEnd) {
        return new BoardMetrics(
                null, boardId, sprintId, metricPeriodStart, metricPeriodEnd,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false,
                0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDateTime.now(), null
        );
    }

    /**
     * Creates a BoardMetrics instance with calculated velocity metrics.
     *
     * @param boardId The board ID
     * @param sprintId The sprint ID
     * @param metricPeriodStart Start of the metrics period
     * @param metricPeriodEnd End of the metrics period
     * @param velocityStoryPoints Velocity in story points
     * @param velocityIssueCount Velocity in issue count
     * @param plannedStoryPoints Originally planned story points
     * @param completedStoryPoints Actually completed story points
     * @return A new BoardMetrics instance with velocity data
     */
    public static BoardMetrics withVelocityMetrics(Long boardId, Long sprintId,
                                                 LocalDateTime metricPeriodStart, LocalDateTime metricPeriodEnd,
                                                 BigDecimal velocityStoryPoints, Integer velocityIssueCount,
                                                 BigDecimal plannedStoryPoints, BigDecimal completedStoryPoints) {
        var baseMetrics = create(boardId, sprintId, metricPeriodStart, metricPeriodEnd);
        return new BoardMetrics(
                baseMetrics.id, baseMetrics.boardId, baseMetrics.sprintId,
                baseMetrics.metricPeriodStart, baseMetrics.metricPeriodEnd,
                velocityStoryPoints, velocityIssueCount, plannedStoryPoints, completedStoryPoints,
                baseMetrics.defectCount, baseMetrics.defectRate, baseMetrics.escapedDefects, baseMetrics.defectDensity,
                baseMetrics.cycleTimeAvg, baseMetrics.cycleTimeMedian, baseMetrics.leadTimeAvg, baseMetrics.leadTimeMedian,
                baseMetrics.scopeChangeCount, baseMetrics.scopeChurnRate, baseMetrics.addedStoryPoints, baseMetrics.removedStoryPoints,
                baseMetrics.commitmentReliability, baseMetrics.sprintGoalSuccess,
                baseMetrics.throughputIssues, baseMetrics.throughputStoryPoints,
                baseMetrics.teamCapacityHours, baseMetrics.utilizationRate,
                baseMetrics.createdAt, baseMetrics.updatedAt
        );
    }

    /**
     * Updates this metrics instance with quality metrics.
     *
     * @param defectCount Number of defects
     * @param defectRate Defect rate as percentage (0.0 to 1.0)
     * @param escapedDefects Number of escaped defects
     * @param defectDensity Defects per story point
     * @return Updated BoardMetrics instance
     */
    public BoardMetrics withQualityMetrics(Integer defectCount, BigDecimal defectRate,
                                         Integer escapedDefects, BigDecimal defectDensity) {
        return new BoardMetrics(
                this.id, this.boardId, this.sprintId, this.metricPeriodStart, this.metricPeriodEnd,
                this.velocityStoryPoints, this.velocityIssueCount, this.plannedStoryPoints, this.completedStoryPoints,
                defectCount, defectRate, escapedDefects, defectDensity,
                this.cycleTimeAvg, this.cycleTimeMedian, this.leadTimeAvg, this.leadTimeMedian,
                this.scopeChangeCount, this.scopeChurnRate, this.addedStoryPoints, this.removedStoryPoints,
                this.commitmentReliability, this.sprintGoalSuccess,
                this.throughputIssues, this.throughputStoryPoints,
                this.teamCapacityHours, this.utilizationRate,
                this.createdAt, LocalDateTime.now()
        );
    }

    /**
     * Updates this metrics instance with flow metrics.
     *
     * @param cycleTimeAvg Average cycle time in hours
     * @param cycleTimeMedian Median cycle time in hours
     * @param leadTimeAvg Average lead time in hours
     * @param leadTimeMedian Median lead time in hours
     * @return Updated BoardMetrics instance
     */
    public BoardMetrics withFlowMetrics(BigDecimal cycleTimeAvg, BigDecimal cycleTimeMedian,
                                      BigDecimal leadTimeAvg, BigDecimal leadTimeMedian) {
        return new BoardMetrics(
                this.id, this.boardId, this.sprintId, this.metricPeriodStart, this.metricPeriodEnd,
                this.velocityStoryPoints, this.velocityIssueCount, this.plannedStoryPoints, this.completedStoryPoints,
                this.defectCount, this.defectRate, this.escapedDefects, this.defectDensity,
                cycleTimeAvg, cycleTimeMedian, leadTimeAvg, leadTimeMedian,
                this.scopeChangeCount, this.scopeChurnRate, this.addedStoryPoints, this.removedStoryPoints,
                this.commitmentReliability, this.sprintGoalSuccess,
                this.throughputIssues, this.throughputStoryPoints,
                this.teamCapacityHours, this.utilizationRate,
                this.createdAt, LocalDateTime.now()
        );
    }

    /**
     * Updates this metrics instance with churn metrics.
     *
     * @param scopeChangeCount Number of scope changes
     * @param scopeChurnRate Scope churn rate as percentage (0.0 to 1.0)
     * @param addedStoryPoints Story points added during sprint
     * @param removedStoryPoints Story points removed during sprint
     * @return Updated BoardMetrics instance
     */
    public BoardMetrics withChurnMetrics(Integer scopeChangeCount, BigDecimal scopeChurnRate,
                                       BigDecimal addedStoryPoints, BigDecimal removedStoryPoints) {
        return new BoardMetrics(
                this.id, this.boardId, this.sprintId, this.metricPeriodStart, this.metricPeriodEnd,
                this.velocityStoryPoints, this.velocityIssueCount, this.plannedStoryPoints, this.completedStoryPoints,
                this.defectCount, this.defectRate, this.escapedDefects, this.defectDensity,
                this.cycleTimeAvg, this.cycleTimeMedian, this.leadTimeAvg, this.leadTimeMedian,
                scopeChangeCount, scopeChurnRate, addedStoryPoints, removedStoryPoints,
                this.commitmentReliability, this.sprintGoalSuccess,
                this.throughputIssues, this.throughputStoryPoints,
                this.teamCapacityHours, this.utilizationRate,
                this.createdAt, LocalDateTime.now()
        );
    }

    /**
     * Calculates the velocity achievement rate.
     *
     * @return Percentage of planned vs completed story points (0.0 to 1.0+)
     */
    public BigDecimal getVelocityAchievementRate() {
        if (plannedStoryPoints != null && plannedStoryPoints.compareTo(BigDecimal.ZERO) > 0) {
            return completedStoryPoints.divide(plannedStoryPoints, 4, java.math.RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Checks if the sprint goal was successfully achieved.
     *
     * @return true if sprint goal success is true and velocity achievement >= 80%
     */
    public boolean isSprintSuccessful() {
        return Boolean.TRUE.equals(sprintGoalSuccess) &&
               getVelocityAchievementRate().compareTo(new BigDecimal("0.8")) >= 0;
    }

    /**
     * Creates a copy of this BoardMetrics with a new ID.
     * Useful for updating existing metrics in the database.
     *
     * @param newId The new ID to assign
     * @return BoardMetrics instance with the specified ID
     */
    public BoardMetrics withId(Long newId) {
        return new BoardMetrics(
                newId, this.boardId, this.sprintId, this.metricPeriodStart, this.metricPeriodEnd,
                this.velocityStoryPoints, this.velocityIssueCount, this.plannedStoryPoints, this.completedStoryPoints,
                this.defectCount, this.defectRate, this.escapedDefects, this.defectDensity,
                this.cycleTimeAvg, this.cycleTimeMedian, this.leadTimeAvg, this.leadTimeMedian,
                this.scopeChangeCount, this.scopeChurnRate, this.addedStoryPoints, this.removedStoryPoints,
                this.commitmentReliability, this.sprintGoalSuccess,
                this.throughputIssues, this.throughputStoryPoints,
                this.teamCapacityHours, this.utilizationRate,
                this.createdAt, LocalDateTime.now()
        );
    }
}

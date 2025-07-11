package com.example.metrics.jira_metrics.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing calculated JIRA metrics and insights for a board and time period.
 * Includes velocity, quality, flow, churn, predictability, and team metrics.
 * Supports both sprint-based and issue-based metrics calculation.
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

        @Column("metric_type")
        String metricType, // "SPRINT_BASED" or "ISSUE_BASED"

        @Column("board_type")
        String boardType, // "scrum", "kanban", "simple"

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

        // Churn metrics (applicable mainly for sprint-based)
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

        @Column("team_utilization")
        BigDecimal teamUtilization,

        @Column("team_focus_factor")
        BigDecimal teamFocusFactor,

        // Issue-based specific metrics
        @Column("issues_in_progress")
        Integer issuesInProgress,

        @Column("issues_in_backlog")
        Integer issuesInBacklog,

        @Column("issues_done")
        Integer issuesDone,

        @Column("wip_limit_adherence")
        BigDecimal wipLimitAdherence,

        @Column("created_at")
        LocalDateTime createdAt
) {

    /**
     * Enumeration for metric calculation types.
     */
    public enum MetricType {
        SPRINT_BASED("SPRINT_BASED"),
        ISSUE_BASED("ISSUE_BASED");

        private final String value;

        MetricType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Creates a new BoardMetrics instance for sprint-based calculations.
     *
     * @param boardId Board identifier
     * @param sprintId Sprint identifier
     * @param periodStart Metric calculation period start
     * @param periodEnd Metric calculation period end
     * @param boardType Type of board
     * @return New BoardMetrics instance
     */
    public static BoardMetrics createSprintBased(Long boardId, Long sprintId,
                                                LocalDateTime periodStart, LocalDateTime periodEnd, String boardType) {
        return new BoardMetrics(
                null, // id will be generated
                boardId,
                sprintId,
                periodStart,
                periodEnd,
                MetricType.SPRINT_BASED.getValue(),
                boardType,
                null, null, null, null, // velocity metrics - to be calculated
                null, null, null, null, // quality metrics - to be calculated
                null, null, null, null, // flow metrics - to be calculated
                null, null, null, null, // churn metrics - to be calculated
                null, null, // predictability metrics - to be calculated
                null, null, // throughput metrics - to be calculated
                null, null, null, // team metrics - to be calculated
                null, null, null, null, // issue-based metrics - not applicable
                LocalDateTime.now()
        );
    }

    /**
     * Creates a new BoardMetrics instance for issue-based calculations.
     *
     * @param boardId Board identifier
     * @param periodStart Metric calculation period start
     * @param periodEnd Metric calculation period end
     * @param boardType Type of board
     * @return New BoardMetrics instance
     */
    public static BoardMetrics createIssueBased(Long boardId, LocalDateTime periodStart,
                                               LocalDateTime periodEnd, String boardType) {
        return new BoardMetrics(
                null, // id will be generated
                boardId,
                null, // no sprint ID for issue-based metrics
                periodStart,
                periodEnd,
                MetricType.ISSUE_BASED.getValue(),
                boardType,
                null, null, null, null, // velocity metrics - to be calculated
                null, null, null, null, // quality metrics - to be calculated
                null, null, null, null, // flow metrics - to be calculated
                null, null, null, null, // churn metrics - not applicable for issue-based
                null, null, // predictability metrics - modified for issue-based
                null, null, // throughput metrics - to be calculated
                null, null, null, // team metrics - to be calculated
                null, null, null, null, // issue-based metrics - to be calculated
                LocalDateTime.now()
        );
    }

    /**
     * Checks if these metrics are sprint-based.
     *
     * @return true if metrics are calculated based on sprint data
     */
    public boolean isSprintBased() {
        return MetricType.SPRINT_BASED.getValue().equals(metricType);
    }

    /**
     * Checks if these metrics are issue-based.
     *
     * @return true if metrics are calculated based on issue data only
     */
    public boolean isIssueBased() {
        return MetricType.ISSUE_BASED.getValue().equals(metricType);
    }
}

package com.example.metrics.jira_metrics.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Entity representing a JIRA issue with comprehensive tracking information.
 * Includes assignee, status, story points, time tracking, and dates.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Table("issues")
public record Issue(
        @Id
        Long id,

        @Column("issue_id")
        String issueId,

        @Column("issue_key")
        String issueKey,

        @Column("board_id")
        Long boardId,

        @Column("sprint_id")
        Long sprintId,

        @Column("issue_type")
        String issueType,

        @Column("status")
        String status,

        @Column("priority")
        String priority,

        @Column("assignee_account_id")
        String assigneeAccountId,

        @Column("assignee_display_name")
        String assigneeDisplayName,

        @Column("reporter_account_id")
        String reporterAccountId,

        @Column("reporter_display_name")
        String reporterDisplayName,

        @Column("summary")
        String summary,

        @Column("description")
        String description,

        @Column("story_points")
        BigDecimal storyPoints,

        @Column("original_estimate")
        Long originalEstimate,

        @Column("remaining_estimate")
        Long remainingEstimate,

        @Column("time_spent")
        Long timeSpent,

        @Column("created_date")
        LocalDateTime createdDate,

        @Column("updated_date")
        LocalDateTime updatedDate,

        @Column("resolved_date")
        LocalDateTime resolvedDate,

        @Column("due_date")
        LocalDateTime dueDate,

        @Column("labels")
        String labels,

        @Column("components")
        String components,

        @Column("fix_versions")
        String fixVersions,

        @Column("created_at")
        LocalDateTime createdAt,

        @Column("updated_at")
        LocalDateTime updatedAt
) {

    /**
     * Common issue types in JIRA.
     */
    public enum IssueType {
        STORY, BUG, TASK, EPIC, SUB_TASK, IMPROVEMENT, NEW_FEATURE
    }

    /**
     * Common issue statuses in JIRA workflows.
     */
    public enum Status {
        TO_DO, IN_PROGRESS, DONE, BLOCKED, IN_REVIEW, TESTING, READY_FOR_DEPLOY
    }

    /**
     * Creates a new Issue instance with current timestamp.
     *
     * @param issueId The JIRA issue ID
     * @param issueKey The JIRA issue key (e.g., "PROJ-123")
     * @param boardId The associated board ID
     * @param sprintId The associated sprint ID
     * @param issueType The type of issue
     * @param status The current status
     * @param priority The issue priority
     * @param assigneeAccountId The assignee's account ID
     * @param assigneeDisplayName The assignee's display name
     * @param reporterAccountId The reporter's account ID
     * @param reporterDisplayName The reporter's display name
     * @param summary The issue summary
     * @param description The issue description
     * @param storyPoints The story points estimate
     * @param originalEstimate Original time estimate in seconds
     * @param remainingEstimate Remaining time estimate in seconds
     * @param timeSpent Time spent in seconds
     * @param createdDate When the issue was created
     * @param updatedDate When the issue was last updated
     * @param resolvedDate When the issue was resolved
     * @param dueDate The issue due date
     * @param labels JSON string of labels
     * @param components JSON string of components
     * @param fixVersions JSON string of fix versions
     * @return A new Issue instance
     */
    public static Issue create(String issueId, String issueKey, Long boardId, Long sprintId,
                             String issueType, String status, String priority,
                             String assigneeAccountId, String assigneeDisplayName,
                             String reporterAccountId, String reporterDisplayName,
                             String summary, String description, BigDecimal storyPoints,
                             Long originalEstimate, Long remainingEstimate, Long timeSpent,
                             LocalDateTime createdDate, LocalDateTime updatedDate,
                             LocalDateTime resolvedDate, LocalDateTime dueDate,
                             String labels, String components, String fixVersions) {
        return new Issue(
                null, issueId, issueKey, boardId, sprintId, issueType, status, priority,
                assigneeAccountId, assigneeDisplayName, reporterAccountId, reporterDisplayName,
                summary, description, storyPoints, originalEstimate, remainingEstimate, timeSpent,
                createdDate, updatedDate, resolvedDate, dueDate, labels, components, fixVersions,
                LocalDateTime.now(), null
        );
    }

    /**
     * Checks if the issue is resolved.
     *
     * @return true if the issue has a resolved date
     */
    public boolean isResolved() {
        return this.resolvedDate != null;
    }

    /**
     * Checks if the issue is a defect/bug.
     *
     * @return true if the issue type is BUG
     */
    public boolean isDefect() {
        return IssueType.BUG.name().equalsIgnoreCase(this.issueType);
    }

    /**
     * Checks if the issue is overdue.
     *
     * @return true if the issue has a due date in the past and is not resolved
     */
    public boolean isOverdue() {
        return this.dueDate != null &&
               this.dueDate.isBefore(LocalDateTime.now()) &&
               !isResolved();
    }

    /**
     * Gets the cycle time in hours if the issue is resolved.
     *
     * @return Optional containing cycle time in hours, empty if not resolved
     */
    public Optional<Double> getCycleTimeHours() {
        if (this.createdDate != null && this.resolvedDate != null) {
            long hours = java.time.Duration.between(this.createdDate, this.resolvedDate).toHours();
            return Optional.of((double) hours);
        }
        return Optional.empty();
    }

    /**
     * Gets the story points as a double value.
     *
     * @return Story points as double, or 0.0 if null
     */
    public double getStoryPointsAsDouble() {
        return this.storyPoints != null ? this.storyPoints.doubleValue() : 0.0;
    }
}

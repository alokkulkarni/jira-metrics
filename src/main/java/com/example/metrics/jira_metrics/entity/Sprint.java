package com.example.metrics.jira_metrics.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity representing a JIRA sprint with its lifecycle information.
 * Tracks sprint state, dates, goals, and associated board.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Table("sprints")
public record Sprint(
        @Id
        Long id,

        @Column("sprint_id")
        Long sprintId,

        @Column("board_id")
        Long boardId,

        @Column("sprint_name")
        String sprintName,

        @Column("sprint_state")
        String sprintState,

        @Column("start_date")
        LocalDateTime startDate,

        @Column("end_date")
        LocalDateTime endDate,

        @Column("complete_date")
        LocalDateTime completeDate,

        @Column("goal")
        String goal,

        @Column("created_at")
        LocalDateTime createdAt,

        @Column("updated_at")
        LocalDateTime updatedAt
) {

    /**
     * Enumeration of possible sprint states.
     */
    public enum SprintState {
        FUTURE, ACTIVE, CLOSED
    }

    /**
     * Creates a new Sprint instance with current timestamp.
     *
     * @param sprintId The JIRA sprint ID
     * @param boardId The associated board ID
     * @param sprintName The sprint name
     * @param sprintState The current sprint state
     * @param startDate The sprint start date
     * @param endDate The sprint end date
     * @param completeDate The sprint completion date
     * @param goal The sprint goal description
     * @return A new Sprint instance
     */
    public static Sprint create(Long sprintId, Long boardId, String sprintName, String sprintState,
                              LocalDateTime startDate, LocalDateTime endDate,
                              LocalDateTime completeDate, String goal) {
        return new Sprint(
                null,
                sprintId,
                boardId,
                sprintName,
                sprintState,
                startDate,
                endDate,
                completeDate,
                goal,
                LocalDateTime.now(),
                null
        );
    }

    /**
     * Creates an updated copy of this Sprint with new information.
     *
     * @param sprintName The updated sprint name
     * @param sprintState The updated sprint state
     * @param startDate The updated start date
     * @param endDate The updated end date
     * @param completeDate The updated completion date
     * @param goal The updated sprint goal
     * @return An updated Sprint instance
     */
    public Sprint withUpdates(String sprintName, String sprintState, LocalDateTime startDate,
                            LocalDateTime endDate, LocalDateTime completeDate, String goal) {
        return new Sprint(
                this.id,
                this.sprintId,
                this.boardId,
                sprintName,
                sprintState,
                startDate,
                endDate,
                completeDate,
                goal,
                this.createdAt,
                LocalDateTime.now()
        );
    }

    /**
     * Checks if the sprint is currently active.
     *
     * @return true if sprint state is ACTIVE
     */
    public boolean isActive() {
        return SprintState.ACTIVE.name().equalsIgnoreCase(this.sprintState);
    }

    /**
     * Checks if the sprint is completed.
     *
     * @return true if sprint state is CLOSED and has completion date
     */
    public boolean isCompleted() {
        return SprintState.CLOSED.name().equalsIgnoreCase(this.sprintState) &&
               this.completeDate != null;
    }
}

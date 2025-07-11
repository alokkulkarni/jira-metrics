package com.example.metrics.jira_metrics.repository;

import com.example.metrics.jira_metrics.entity.Issue;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Issue entity operations.
 * Provides methods to store and retrieve issue data with filtering and analytics support.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Repository
public interface IssueRepository extends CrudRepository<Issue, Long> {

    /**
     * Finds issue by issue ID.
     *
     * @param issueId The JIRA issue ID
     * @return Optional containing issue if found
     */
    @Query("SELECT * FROM issues WHERE issue_id = :issueId")
    Optional<Issue> findByIssueId(@Param("issueId") String issueId);

    /**
     * Finds issue by issue key.
     *
     * @param issueKey The JIRA issue key (e.g., "PROJ-123")
     * @return Optional containing issue if found
     */
    @Query("SELECT * FROM issues WHERE issue_key = :issueKey")
    Optional<Issue> findByIssueKey(@Param("issueKey") String issueKey);

    /**
     * Finds all issues for a specific board.
     *
     * @param boardId The board ID
     * @return List of issues for the board
     */
    @Query("SELECT * FROM issues WHERE board_id = :boardId ORDER BY created_date DESC")
    List<Issue> findByBoardId(@Param("boardId") Long boardId);

    /**
     * Finds all issues for a specific sprint.
     *
     * @param sprintId The sprint ID
     * @return List of issues for the sprint
     */
    @Query("SELECT * FROM issues WHERE sprint_id = :sprintId ORDER BY created_date DESC")
    List<Issue> findBySprintId(@Param("sprintId") Long sprintId);

    /**
     * Finds resolved issues for a sprint.
     *
     * @param sprintId The sprint ID
     * @return List of resolved issues
     */
    @Query("SELECT * FROM issues WHERE sprint_id = :sprintId AND resolved_date IS NOT NULL ORDER BY resolved_date DESC")
    List<Issue> findResolvedIssuesInSprint(@Param("sprintId") Long sprintId);

    /**
     * Finds defects (bugs) for a sprint.
     *
     * @param sprintId The sprint ID
     * @return List of defect issues
     */
    @Query("SELECT * FROM issues WHERE sprint_id = :sprintId AND UPPER(issue_type) = 'BUG' ORDER BY created_date DESC")
    List<Issue> findDefectsInSprint(@Param("sprintId") Long sprintId);

    /**
     * Finds issues by assignee for a board.
     *
     * @param boardId The board ID
     * @param assigneeAccountId The assignee's account ID
     * @return List of issues assigned to the user
     */
    @Query("SELECT * FROM issues WHERE board_id = :boardId AND assignee_account_id = :assigneeAccountId ORDER BY created_date DESC")
    List<Issue> findByBoardIdAndAssignee(@Param("boardId") Long boardId, @Param("assigneeAccountId") String assigneeAccountId);

    /**
     * Finds issues created within a date range.
     *
     * @param boardId The board ID
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of issues created in the date range
     */
    @Query("SELECT * FROM issues WHERE board_id = :boardId AND created_date BETWEEN :startDate AND :endDate ORDER BY created_date DESC")
    List<Issue> findIssuesCreatedInRange(@Param("boardId") Long boardId,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Finds issues resolved within a date range.
     *
     * @param boardId The board ID
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of issues resolved in the date range
     */
    @Query("SELECT * FROM issues WHERE board_id = :boardId AND resolved_date BETWEEN :startDate AND :endDate ORDER BY resolved_date DESC")
    List<Issue> findIssuesResolvedInRange(@Param("boardId") Long boardId,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    /**
     * Counts total story points for a sprint.
     *
     * @param sprintId The sprint ID
     * @return Total story points in the sprint
     */
    @Query("SELECT COALESCE(SUM(story_points), 0) FROM issues WHERE sprint_id = :sprintId")
    Double getTotalStoryPointsInSprint(@Param("sprintId") Long sprintId);

    /**
     * Counts completed story points for a sprint.
     *
     * @param sprintId The sprint ID
     * @return Completed story points in the sprint
     */
    @Query("SELECT COALESCE(SUM(story_points), 0) FROM issues WHERE sprint_id = :sprintId AND resolved_date IS NOT NULL")
    Double getCompletedStoryPointsInSprint(@Param("sprintId") Long sprintId);

    /**
     * Counts defects for a sprint.
     *
     * @param sprintId The sprint ID
     * @return Number of defects in the sprint
     */
    @Query("SELECT COUNT(*) FROM issues WHERE sprint_id = :sprintId AND UPPER(issue_type) = 'BUG'")
    Long getDefectCountInSprint(@Param("sprintId") Long sprintId);
}

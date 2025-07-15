package com.example.metrics.jira_metrics.repository;

import com.example.metrics.jira_metrics.entity.Issue;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Issue entity operations.
 * Provides methods for issue data access and queries.
 * Supports both sprint-based and non-sprint boards.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Repository
public interface IssueRepository extends CrudRepository<Issue, Long> {

    /**
     * Finds an issue by its JIRA issue ID.
     *
     * @param issueId The JIRA issue ID
     * @return Optional containing the issue if found
     */
    Optional<Issue> findByIssueId(String issueId);

    /**
     * Finds an issue by its JIRA issue key.
     *
     * @param issueKey The JIRA issue key (e.g., "PROJ-123")
     * @return Optional containing the issue if found
     */
    Optional<Issue> findByIssueKey(String issueKey);

    /**
     * Finds all issues for a specific board.
     * Works for both sprint-based and non-sprint boards.
     *
     * @param boardId The board ID
     * @return List of issues for the board
     */
    List<Issue> findByBoardId(Long boardId);

    /**
     * Finds all issues for a specific sprint.
     * Only applicable to sprint-based boards.
     *
     * @param sprintId The sprint ID
     * @return List of issues in the sprint
     */
    List<Issue> findBySprintId(Long sprintId);

    /**
     * Finds all issues for a board with a specific status.
     *
     * @param boardId The board ID
     * @param status The issue status
     * @return List of issues matching the criteria
     */
    List<Issue> findByBoardIdAndStatus(Long boardId, String status);

    /**
     * Finds all issues for a sprint with a specific status.
     *
     * @param sprintId The sprint ID
     * @param status The issue status
     * @return List of issues matching the criteria
     */
    List<Issue> findBySprintIdAndStatus(Long sprintId, String status);

    /**
     * Finds all issues assigned to a specific user.
     *
     * @param assigneeAccountId The assignee's account ID
     * @return List of issues assigned to the user
     */
    List<Issue> findByAssigneeAccountId(String assigneeAccountId);

    /**
     * Finds all issues for a board that are not linked to any sprint.
     * Useful for Kanban boards or unassigned issues.
     *
     * @param boardId The board ID
     * @return List of issues without sprint assignment
     */
    List<Issue> findByBoardIdAndSprintIdIsNull(Long boardId);

    /**
     * Finds all issues for a board that are linked to sprints.
     * Useful for Scrum boards.
     *
     * @param boardId The board ID
     * @return List of issues with sprint assignment
     */
    List<Issue> findByBoardIdAndSprintIdIsNotNull(Long boardId);

    /**
     * Counts the number of issues for a specific board.
     *
     * @param boardId The board ID
     * @return Number of issues for the board
     */
    long countByBoardId(Long boardId);

    /**
     * Counts the number of issues for a specific sprint.
     *
     * @param sprintId The sprint ID
     * @return Number of issues in the sprint
     */
    long countBySprintId(Long sprintId);

    /**
     * Counts issues by board and status.
     *
     * @param boardId The board ID
     * @param status The issue status
     * @return Number of issues matching the criteria
     */
    long countByBoardIdAndStatus(Long boardId, String status);

    /**
     * Finds issues by board and issue type.
     *
     * @param boardId The board ID
     * @param issueType The issue type (e.g., "Story", "Bug", "Task")
     * @return List of issues matching the criteria
     */
    List<Issue> findByBoardIdAndIssueType(Long boardId, String issueType);

    /**
     * Finds all issues with story points for a board.
     * Useful for velocity calculations.
     *
     * @param boardId The board ID
     * @return List of issues that have story points assigned
     */
    @Query("SELECT * FROM issues WHERE board_id = :boardId AND story_points IS NOT NULL AND story_points > 0")
    List<Issue> findByBoardIdWithStoryPoints(@Param("boardId") Long boardId);

    /**
     * Finds all issues with story points for a sprint.
     * Useful for sprint velocity calculations.
     *
     * @param sprintId The sprint ID
     * @return List of issues that have story points assigned
     */
    @Query("SELECT * FROM issues WHERE sprint_id = :sprintId AND story_points IS NOT NULL AND story_points > 0")
    List<Issue> findBySprintIdWithStoryPoints(@Param("sprintId") Long sprintId);

    /**
     * Deletes all issues for a specific board.
     * Useful for cleanup operations.
     *
     * @param boardId The board ID
     */
    void deleteByBoardId(Long boardId);

    /**
     * Deletes all issues for a specific sprint.
     * Useful for cleanup operations.
     *
     * @param sprintId The sprint ID
     */
    void deleteBySprintId(Long sprintId);
}

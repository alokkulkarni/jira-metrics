package com.example.metrics.jira_metrics.repository;

import com.example.metrics.jira_metrics.entity.Sprint;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Sprint entity operations.
 * Provides methods for sprint data access and queries.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Repository
public interface SprintRepository extends CrudRepository<Sprint, Long> {

    /**
     * Finds a sprint by its JIRA sprint ID.
     *
     * @param sprintId The JIRA sprint ID
     * @return Optional containing the sprint if found
     */
    Optional<Sprint> findBySprintId(Long sprintId);

    /**
     * Finds all sprints for a specific board.
     *
     * @param boardId The board ID
     * @return List of sprints for the board
     */
    List<Sprint> findByBoardId(Long boardId);

    /**
     * Finds all sprints for a specific board ordered by start date descending.
     * Useful for getting the most recent sprints first.
     *
     * @param boardId The board ID
     * @return List of sprints ordered by start date (newest first)
     */
    List<Sprint> findByBoardIdOrderByStartDateDesc(Long boardId);

    /**
     * Finds the most recent sprint for a board.
     *
     * @param boardId The board ID
     * @return Optional containing the latest sprint
     */
    @Query("SELECT * FROM sprints WHERE board_id = :boardId ORDER BY start_date DESC LIMIT 1")
    Optional<Sprint> findTopByBoardIdOrderByStartDateDesc(@Param("boardId") Long boardId);

    /**
     * Finds active sprint for a board.
     *
     * @param boardId The board ID
     * @param sprintState The sprint state (typically "active")
     * @return Optional containing the active sprint
     */
    Optional<Sprint> findFirstByBoardIdAndSprintState(Long boardId, String sprintState);

    /**
     * Finds all sprints for a board with a specific state.
     *
     * @param boardId The board ID
     * @param sprintState The sprint state
     * @return List of sprints matching the criteria
     */
    List<Sprint> findByBoardIdAndSprintState(Long boardId, String sprintState);

    /**
     * Finds all sprints with a specific state.
     *
     * @param sprintState The sprint state (active, closed, future)
     * @return List of sprints with the specified state
     */
    List<Sprint> findBySprintState(String sprintState);

    /**
     * Finds recent completed sprints for metrics calculation.
     *
     * @param boardId The board ID
     * @param limit Maximum number of sprints to return
     * @return List of recent completed sprints
     */
    @Query("SELECT * FROM sprints WHERE board_id = :boardId AND sprint_state = 'closed' ORDER BY complete_date DESC LIMIT :limit")
    List<Sprint> findRecentCompletedSprints(@Param("boardId") Long boardId, @Param("limit") int limit);

    /**
     * Counts the number of sprints for a specific board.
     *
     * @param boardId The board ID
     * @return Number of sprints for the board
     */
    long countByBoardId(Long boardId);

    /**
     * Deletes all sprints for a specific board.
     * Useful for cleanup operations.
     *
     * @param boardId The board ID
     */
    void deleteByBoardId(Long boardId);
}

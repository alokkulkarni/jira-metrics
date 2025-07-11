package com.example.metrics.jira_metrics.repository;

import com.example.metrics.jira_metrics.entity.Sprint;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Sprint entity operations.
 * Provides methods to store and retrieve sprint data with various filtering options.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Repository
public interface SprintRepository extends CrudRepository<Sprint, Long> {

    /**
     * Finds sprint by sprint ID.
     *
     * @param sprintId The sprint ID
     * @return Optional containing sprint if found
     */
    @Query("SELECT * FROM sprints WHERE sprint_id = :sprintId")
    Optional<Sprint> findBySprintId(@Param("sprintId") Long sprintId);

    /**
     * Finds all sprints for a specific board.
     *
     * @param boardId The board ID
     * @return List of sprints for the board
     */
    @Query("SELECT * FROM sprints WHERE board_id = :boardId ORDER BY start_date DESC")
    List<Sprint> findByBoardId(@Param("boardId") Long boardId);

    /**
     * Finds sprints by board ID and sprint state.
     *
     * @param boardId The board ID
     * @param sprintState The sprint state
     * @return List of sprints matching criteria
     */
    @Query("SELECT * FROM sprints WHERE board_id = :boardId AND sprint_state = :sprintState ORDER BY start_date DESC")
    List<Sprint> findByBoardIdAndSprintState(@Param("boardId") Long boardId, @Param("sprintState") String sprintState);

    /**
     * Finds the currently active sprint for a board.
     *
     * @param boardId The board ID
     * @return Optional containing active sprint if found
     */
    @Query("SELECT * FROM sprints WHERE board_id = :boardId AND sprint_state = 'ACTIVE' LIMIT 1")
    Optional<Sprint> findActiveSprint(@Param("boardId") Long boardId);

    /**
     * Finds completed sprints within a date range.
     *
     * @param boardId The board ID
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of completed sprints in the date range
     */
    @Query("SELECT * FROM sprints WHERE board_id = :boardId AND sprint_state = 'CLOSED' " +
           "AND complete_date BETWEEN :startDate AND :endDate ORDER BY complete_date DESC")
    List<Sprint> findCompletedSprintsInRange(@Param("boardId") Long boardId,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Finds the most recent completed sprints for a board.
     *
     * @param boardId The board ID
     * @param limit Maximum number of sprints to return
     * @return List of recent completed sprints
     */
    @Query("SELECT * FROM sprints WHERE board_id = :boardId AND sprint_state = 'CLOSED' " +
           "ORDER BY complete_date DESC LIMIT :limit")
    List<Sprint> findRecentCompletedSprints(@Param("boardId") Long boardId, @Param("limit") int limit);
}

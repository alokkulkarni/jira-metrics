package com.example.metrics.jira_metrics.repository;

import com.example.metrics.jira_metrics.entity.BoardMetrics;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for BoardMetrics entity operations.
 * Provides methods to store and retrieve calculated JIRA metrics and insights.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Repository
public interface BoardMetricsRepository extends CrudRepository<BoardMetrics, Long> {

    /**
     * Finds metrics for a specific board and sprint.
     *
     * @param boardId The board ID
     * @param sprintId The sprint ID
     * @return Optional containing metrics if found
     */
    @Query("SELECT * FROM board_metrics WHERE board_id = :boardId AND sprint_id = :sprintId")
    Optional<BoardMetrics> findByBoardIdAndSprintId(@Param("boardId") Long boardId, @Param("sprintId") Long sprintId);

    /**
     * Finds all metrics for a specific board.
     *
     * @param boardId The board ID
     * @return List of metrics for the board ordered by period start
     */
    @Query("SELECT * FROM board_metrics WHERE board_id = :boardId ORDER BY metric_period_start DESC")
    List<BoardMetrics> findByBoardId(@Param("boardId") Long boardId);

    /**
     * Finds metrics within a date range for a board.
     *
     * @param boardId The board ID
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of metrics in the date range
     */
    @Query("SELECT * FROM board_metrics WHERE board_id = :boardId " +
           "AND metric_period_start >= :startDate AND metric_period_end <= :endDate " +
           "ORDER BY metric_period_start DESC")
    List<BoardMetrics> findByBoardIdAndPeriodRange(@Param("boardId") Long boardId,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);

    /**
     * Finds the most recent metrics for a board.
     *
     * @param boardId The board ID
     * @param limit Maximum number of metrics to return
     * @return List of recent metrics
     */
    @Query("SELECT * FROM board_metrics WHERE board_id = :boardId " +
           "ORDER BY metric_period_start DESC LIMIT :limit")
    List<BoardMetrics> findRecentMetricsByBoardId(@Param("boardId") Long boardId, @Param("limit") int limit);

    /**
     * Finds metrics for completed sprints only.
     *
     * @param boardId The board ID
     * @return List of metrics for completed sprints
     */
    @Query("SELECT bm.* FROM board_metrics bm " +
           "INNER JOIN sprints s ON bm.sprint_id = s.sprint_id " +
           "WHERE bm.board_id = :boardId AND s.sprint_state = 'CLOSED' " +
           "ORDER BY bm.metric_period_start DESC")
    List<BoardMetrics> findCompletedSprintMetrics(@Param("boardId") Long boardId);

    /**
     * Calculates average velocity for recent sprints.
     *
     * @param boardId The board ID
     * @param sprintCount Number of recent sprints to include
     * @return Average velocity in story points
     */
    @Query("SELECT AVG(velocity_story_points) FROM (" +
           "SELECT velocity_story_points FROM board_metrics bm " +
           "INNER JOIN sprints s ON bm.sprint_id = s.sprint_id " +
           "WHERE bm.board_id = :boardId AND s.sprint_state = 'CLOSED' " +
           "ORDER BY bm.metric_period_start DESC LIMIT :sprintCount" +
           ") recent_sprints")
    Double getAverageVelocity(@Param("boardId") Long boardId, @Param("sprintCount") int sprintCount);

    /**
     * Calculates average defect rate for recent sprints.
     *
     * @param boardId The board ID
     * @param sprintCount Number of recent sprints to include
     * @return Average defect rate
     */
    @Query("SELECT AVG(defect_rate) FROM (" +
           "SELECT defect_rate FROM board_metrics bm " +
           "INNER JOIN sprints s ON bm.sprint_id = s.sprint_id " +
           "WHERE bm.board_id = :boardId AND s.sprint_state = 'CLOSED' " +
           "ORDER BY bm.metric_period_start DESC LIMIT :sprintCount" +
           ") recent_sprints")
    Double getAverageDefectRate(@Param("boardId") Long boardId, @Param("sprintCount") int sprintCount);

    /**
     * Calculates average cycle time for recent sprints.
     *
     * @param boardId The board ID
     * @param sprintCount Number of recent sprints to include
     * @return Average cycle time in hours
     */
    @Query("SELECT AVG(cycle_time_avg) FROM (" +
           "SELECT cycle_time_avg FROM board_metrics bm " +
           "INNER JOIN sprints s ON bm.sprint_id = s.sprint_id " +
           "WHERE bm.board_id = :boardId AND s.sprint_state = 'CLOSED' " +
           "ORDER BY bm.metric_period_start DESC LIMIT :sprintCount" +
           ") recent_sprints")
    Double getAverageCycleTime(@Param("boardId") Long boardId, @Param("sprintCount") int sprintCount);

    /**
     * Finds metrics that need to be recalculated (older than threshold).
     *
     * @param threshold DateTime threshold for stale metrics
     * @return List of stale metrics
     */
    @Query("SELECT * FROM board_metrics WHERE updated_at < :threshold OR updated_at IS NULL")
    List<BoardMetrics> findStaleMetrics(@Param("threshold") LocalDateTime threshold);
}

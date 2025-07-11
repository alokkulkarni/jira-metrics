package com.example.metrics.jira_metrics.repository;

import com.example.metrics.jira_metrics.entity.BoardDetails;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for BoardDetails entity operations.
 * Provides methods to store and retrieve detailed board configuration data.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Repository
public interface BoardDetailsRepository extends CrudRepository<BoardDetails, Long> {

    /**
     * Finds board details by board ID.
     *
     * @param boardId The board ID
     * @return Optional containing board details if found
     */
    @Query("SELECT * FROM board_details WHERE board_id = :boardId")
    Optional<BoardDetails> findByBoardId(@Param("boardId") Long boardId);

    /**
     * Finds all board details by board type.
     *
     * @param boardType The board type (scrum, kanban, simple)
     * @return Iterable of board details matching the type
     */
    @Query("SELECT * FROM board_details WHERE board_type = :boardType")
    Iterable<BoardDetails> findByBoardType(@Param("boardType") String boardType);

    /**
     * Checks if board details exist for a given board ID.
     *
     * @param boardId The board ID
     * @return true if board details exist
     */
    @Query("SELECT COUNT(*) > 0 FROM board_details WHERE board_id = :boardId")
    boolean existsByBoardId(@Param("boardId") Long boardId);

    /**
     * Deletes board details by board ID.
     *
     * @param boardId The board ID
     * @return Number of deleted records
     */
    @Query("DELETE FROM board_details WHERE board_id = :boardId")
    int deleteByBoardId(@Param("boardId") Long boardId);
}

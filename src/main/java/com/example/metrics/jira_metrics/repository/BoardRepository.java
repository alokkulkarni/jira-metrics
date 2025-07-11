package com.example.metrics.jira_metrics.repository;

import com.example.metrics.jira_metrics.entity.Board;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Board entity operations.
 * Provides methods to retrieve board configurations for the scheduled job.
 */
@Repository
public interface BoardRepository extends CrudRepository<Board, Long> {

    /**
     * Finds all active boards for processing.
     *
     * @return List of active boards
     */
    @Query("SELECT * FROM boards WHERE is_active = true")
    List<Board> findAllActiveBoards();

    /**
     * Finds a board by its JIRA board ID.
     *
     * @param boardId The JIRA board ID
     * @return Optional board entity
     */
    @Query("SELECT * FROM boards WHERE board_id = :boardId")
    Optional<Board> findByBoardId(@Param("boardId") Long boardId);

    /**
     * Finds boards by project key.
     *
     * @param projectKey The project key
     * @return List of boards for the project
     */
    @Query("SELECT * FROM boards WHERE project_key = :projectKey AND is_active = true")
    List<Board> findByProjectKeyAndIsActiveTrue(@Param("projectKey") String projectKey);
}

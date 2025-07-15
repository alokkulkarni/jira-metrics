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
 * Provides methods for board data access and queries.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Repository
public interface BoardRepository extends CrudRepository<Board, Long> {

    /**
     * Finds a board by its JIRA board ID.
     *
     * @param boardId The JIRA board ID
     * @return Optional containing the board if found
     */
    Optional<Board> findByBoardId(Long boardId);

    /**
     * Finds all active boards.
     *
     * @return List of active boards
     */
    List<Board> findByIsActiveTrue();

    /**
     * Finds all active boards.
     * Alias method for backward compatibility.
     *
     * @return List of active boards
     */
    default List<Board> findAllActiveBoards() {
        return findByIsActiveTrue();
    }

    /**
     * Finds all boards by project key.
     *
     * @param projectKey The project key
     * @return List of boards for the project
     */
    List<Board> findByProjectKey(String projectKey);

    /**
     * Finds all active boards by project key.
     *
     * @param projectKey The project key
     * @return List of active boards for the project
     */
    List<Board> findByProjectKeyAndIsActiveTrue(String projectKey);

    /**
     * Finds all boards by board type.
     *
     * @param boardType The board type
     * @return List of boards of the specified type
     */
    List<Board> findByBoardType(String boardType);

    /**
     * Finds all boards that have sprints enabled.
     *
     * @return List of sprint-enabled boards
     */
    List<Board> findByHasSprintsTrue();

    /**
     * Finds all boards that don't have sprints enabled.
     *
     * @return List of non-sprint boards
     */
    List<Board> findByHasSprintsFalse();
}

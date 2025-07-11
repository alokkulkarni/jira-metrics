package com.example.metrics.jira_metrics.repository;

import com.example.metrics.jira_metrics.entity.JiraData;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for JiraData entity operations.
 * Provides methods to store and retrieve raw JIRA data.
 */
@Repository
public interface JiraDataRepository extends CrudRepository<JiraData, Long> {

    /**
     * Finds JIRA data by board ID and data type.
     *
     * @param boardId  The board ID
     * @param dataType The type of data
     * @return List of JIRA data records
     */
    @Query("SELECT * FROM jira_data WHERE board_id = :boardId AND data_type = :dataType")
    List<JiraData> findByBoardIdAndDataType(@Param("boardId") Long boardId, @Param("dataType") String dataType);

    /**
     * Finds JIRA data by board ID and team ID.
     *
     * @param boardId The board ID
     * @param teamId  The team ID
     * @return List of JIRA data records
     */
    @Query("SELECT * FROM jira_data WHERE board_id = :boardId AND team_id = :teamId")
    List<JiraData> findByBoardIdAndTeamId(@Param("boardId") Long boardId, @Param("teamId") String teamId);

    /**
     * Finds the latest JIRA data for a board and data type.
     *
     * @param boardId  The board ID
     * @param dataType The type of data
     * @return Latest JIRA data record
     */
    @Query("SELECT * FROM jira_data WHERE board_id = :boardId AND data_type = :dataType " +
           "ORDER BY retrieval_timestamp DESC LIMIT 1")
    JiraData findLatestByBoardIdAndDataType(@Param("boardId") Long boardId, @Param("dataType") String dataType);

    /**
     * Finds JIRA data retrieved after a specific timestamp.
     *
     * @param timestamp The timestamp threshold
     * @return List of JIRA data records
     */
    @Query("SELECT * FROM jira_data WHERE retrieval_timestamp > :timestamp")
    List<JiraData> findByRetrievalTimestampAfter(@Param("timestamp") LocalDateTime timestamp);

    /**
     * Counts records by board ID and data type.
     *
     * @param boardId  The board ID
     * @param dataType The type of data
     * @return Count of records
     */
    @Query("SELECT COUNT(*) FROM jira_data WHERE board_id = :boardId AND data_type = :dataType")
    long countByBoardIdAndDataType(@Param("boardId") Long boardId, @Param("dataType") String dataType);
}

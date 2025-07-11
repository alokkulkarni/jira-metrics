package com.example.metrics.jira_metrics.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity representing detailed board configuration information from JIRA.
 * This includes board type, location, filters, and configuration details.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Table("board_details")
public record BoardDetails(
        @Id
        Long id,

        @Column("board_id")
        Long boardId,

        @Column("board_type")
        String boardType,

        @Column("board_location")
        String boardLocation,

        @Column("filter_id")
        Long filterId,

        @Column("can_edit")
        Boolean canEdit,

        @Column("sub_query")
        String subQuery,

        @Column("column_config")
        String columnConfig,

        @Column("estimation_config")
        String estimationConfig,

        @Column("ranking_config")
        String rankingConfig,

        @Column("created_at")
        LocalDateTime createdAt,

        @Column("updated_at")
        LocalDateTime updatedAt
) {

    /**
     * Creates a new BoardDetails instance with current timestamp.
     *
     * @param boardId The JIRA board ID
     * @param boardType The type of board (scrum, kanban, simple)
     * @param boardLocation The board location URL
     * @param filterId The associated filter ID
     * @param canEdit Whether the board can be edited
     * @param subQuery The board's sub-query configuration
     * @param columnConfig JSON string of column configuration
     * @param estimationConfig JSON string of estimation configuration
     * @param rankingConfig JSON string of ranking configuration
     * @return A new BoardDetails instance
     */
    public static BoardDetails create(Long boardId, String boardType, String boardLocation,
                                    Long filterId, Boolean canEdit, String subQuery,
                                    String columnConfig, String estimationConfig, String rankingConfig) {
        return new BoardDetails(
                null,
                boardId,
                boardType,
                boardLocation,
                filterId,
                canEdit,
                subQuery,
                columnConfig,
                estimationConfig,
                rankingConfig,
                LocalDateTime.now(),
                null
        );
    }

    /**
     * Creates an updated copy of this BoardDetails with new timestamp.
     *
     * @param boardType The updated board type
     * @param boardLocation The updated board location
     * @param filterId The updated filter ID
     * @param canEdit The updated edit permission
     * @param subQuery The updated sub-query
     * @param columnConfig The updated column configuration
     * @param estimationConfig The updated estimation configuration
     * @param rankingConfig The updated ranking configuration
     * @return An updated BoardDetails instance
     */
    public BoardDetails withUpdates(String boardType, String boardLocation, Long filterId,
                                  Boolean canEdit, String subQuery, String columnConfig,
                                  String estimationConfig, String rankingConfig) {
        return new BoardDetails(
                this.id,
                this.boardId,
                boardType,
                boardLocation,
                filterId,
                canEdit,
                subQuery,
                columnConfig,
                estimationConfig,
                rankingConfig,
                this.createdAt,
                LocalDateTime.now()
        );
    }
}

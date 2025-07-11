package com.example.metrics.jira_metrics.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing a JIRA board configuration with all board details.
 * Stores comprehensive board information including configuration and validation data.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Table("boards")
public class Board {

    /**
     * Enumeration for board types in JIRA.
     */
    public enum BoardType {
        SCRUM("scrum"),
        KANBAN("kanban"),
        SIMPLE("simple");

        private final String value;

        BoardType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static BoardType fromValue(String value) {
            for (BoardType type : values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return SIMPLE; // Default fallback
        }
    }

    @Id
    @JsonProperty("id")
    private Long id;

    @NotNull
    @Column("board_id")
    @JsonProperty("boardId")
    private Long boardId;

    @NotBlank
    @Column("board_name")
    @JsonProperty("boardName")
    private String boardName;

    @NotBlank
    @Column("project_key")
    @JsonProperty("projectKey")
    private String projectKey;

    @Column("board_type")
    @JsonProperty("boardType")
    private String boardType;

    @Column("has_sprints")
    @JsonProperty("hasSprints")
    private Boolean hasSprints = false;

    @Column("sprint_count")
    @JsonProperty("sprintCount")
    private Integer sprintCount = 0;

    // Additional board configuration fields (formerly in board_details)
    @Column("board_location")
    @JsonProperty("boardLocation")
    private String boardLocation;

    @Column("filter_id")
    @JsonProperty("filterId")
    private Long filterId;

    @Column("can_edit")
    @JsonProperty("canEdit")
    private Boolean canEdit = false;

    @Column("sub_query")
    @JsonProperty("subQuery")
    private String subQuery;

    @Column("column_config")
    @JsonProperty("columnConfig")
    private String columnConfig;

    @Column("estimation_config")
    @JsonProperty("estimationConfig")
    private String estimationConfig;

    @Column("ranking_config")
    @JsonProperty("rankingConfig")
    private String rankingConfig;

    @Column("is_active")
    @JsonProperty("isActive")
    private Boolean isActive = true;

    @Column("created_at")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @Column("updated_at")
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    /**
     * Default constructor for Spring Data JDBC.
     */
    public Board() {
        // Default constructor required for JPA/Spring Data
    }

    /**
     * Constructor with required fields.
     *
     * @param boardId   The JIRA board ID
     * @param boardName The board name
     * @param projectKey The project key
     */
    public Board(Long boardId, String boardName, String projectKey) {
        this.boardId = boardId;
        this.boardName = boardName;
        this.projectKey = projectKey;
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.hasSprints = false;
        this.sprintCount = 0;
        this.canEdit = false;
    }

    /**
     * Constructor with board type and sprint information.
     *
     * @param boardId   The JIRA board ID
     * @param boardName The board name
     * @param projectKey The project key
     * @param boardType The type of board
     * @param hasSprints Whether the board has sprints
     */
    public Board(Long boardId, String boardName, String projectKey, String boardType, Boolean hasSprints) {
        this(boardId, boardName, projectKey);
        this.boardType = boardType;
        this.hasSprints = hasSprints != null ? hasSprints : false;
    }

    /**
     * Factory method to create a comprehensive Board with all configuration.
     *
     * @param boardId The JIRA board ID
     * @param boardName The board name
     * @param projectKey The project key
     * @param boardType The type of board
     * @param boardLocation The board location URL
     * @param filterId The associated filter ID
     * @param canEdit Whether the board can be edited
     * @param columnConfig JSON string of column configuration
     * @param estimationConfig JSON string of estimation configuration
     * @param rankingConfig JSON string of ranking configuration
     * @return A new Board instance with all configuration
     */
    public static Board createWithConfiguration(Long boardId, String boardName, String projectKey,
                                               String boardType, String boardLocation, Long filterId,
                                               Boolean canEdit, String columnConfig, String estimationConfig,
                                               String rankingConfig) {
        var board = new Board(boardId, boardName, projectKey);
        board.setBoardType(boardType);
        board.setBoardLocation(boardLocation);
        board.setFilterId(filterId);
        board.setCanEdit(canEdit);
        board.setColumnConfig(columnConfig);
        board.setEstimationConfig(estimationConfig);
        board.setRankingConfig(rankingConfig);
        return board;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBoardId() {
        return boardId;
    }

    public void setBoardId(Long boardId) {
        this.boardId = boardId;
    }

    public String getBoardName() {
        return boardName;
    }

    public void setBoardName(String boardName) {
        this.boardName = boardName;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getBoardType() {
        return boardType;
    }

    public void setBoardType(String boardType) {
        this.boardType = boardType;
    }

    public Boolean getHasSprints() {
        return hasSprints;
    }

    public void setHasSprints(Boolean hasSprints) {
        this.hasSprints = hasSprints;
    }

    public Integer getSprintCount() {
        return sprintCount;
    }

    public void setSprintCount(Integer sprintCount) {
        this.sprintCount = sprintCount;
    }

    public String getBoardLocation() {
        return boardLocation;
    }

    public void setBoardLocation(String boardLocation) {
        this.boardLocation = boardLocation;
    }

    public Long getFilterId() {
        return filterId;
    }

    public void setFilterId(Long filterId) {
        this.filterId = filterId;
    }

    public Boolean getCanEdit() {
        return canEdit;
    }

    public void setCanEdit(Boolean canEdit) {
        this.canEdit = canEdit;
    }

    public String getSubQuery() {
        return subQuery;
    }

    public void setSubQuery(String subQuery) {
        this.subQuery = subQuery;
    }

    public String getColumnConfig() {
        return columnConfig;
    }

    public void setColumnConfig(String columnConfig) {
        this.columnConfig = columnConfig;
    }

    public String getEstimationConfig() {
        return estimationConfig;
    }

    public void setEstimationConfig(String estimationConfig) {
        this.estimationConfig = estimationConfig;
    }

    public String getRankingConfig() {
        return rankingConfig;
    }

    public void setRankingConfig(String rankingConfig) {
        this.rankingConfig = rankingConfig;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Gets the board type as an enum value.
     *
     * @return BoardType enum value
     */
    public BoardType getBoardTypeEnum() {
        return BoardType.fromValue(this.boardType);
    }

    /**
     * Checks if this board supports sprint-based metrics.
     *
     * @return true if board has sprints and supports sprint metrics
     */
    public boolean supportsSprintMetrics() {
        return Boolean.TRUE.equals(hasSprints) && sprintCount != null && sprintCount > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Board board = (Board) o;
        return Objects.equals(boardId, board.boardId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boardId);
    }

    @Override
    public String toString() {
        return "Board{" +
                "id=" + id +
                ", boardId=" + boardId +
                ", boardName='" + boardName + '\'' +
                ", projectKey='" + projectKey + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}

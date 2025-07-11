package com.example.metrics.jira_metrics.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing raw JIRA data retrieved from boards.
 * Stores the complete JSON response from JIRA API calls.
 */
@Table("jira_data")
public class JiraData {

    @Id
    private Long id;

    @NotNull
    @Column("board_id")
    private Long boardId;

    @Column("team_id")
    private String teamId;

    @NotBlank
    @Column("data_type")
    private String dataType; // e.g., "issues", "sprints", "board_config"

    @Column("raw_data")
    private String rawData;

    @Column("retrieval_timestamp")
    private LocalDateTime retrievalTimestamp;

    @Column("record_count")
    private Integer recordCount;

    @Column("created_at")
    private LocalDateTime createdAt;

    /**
     * Default constructor for Spring Data JDBC.
     */
    public JiraData() {
    }

    /**
     * Constructor with required fields.
     *
     * @param boardId            The board ID
     * @param dataType          The type of data (issues, sprints, etc.)
     * @param rawData           The raw JSON data
     * @param retrievalTimestamp When the data was retrieved
     */
    public JiraData(Long boardId, String dataType, String rawData, LocalDateTime retrievalTimestamp) {
        this.boardId = boardId;
        this.dataType = dataType;
        this.rawData = rawData;
        this.retrievalTimestamp = retrievalTimestamp;
        this.createdAt = LocalDateTime.now();
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

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public LocalDateTime getRetrievalTimestamp() {
        return retrievalTimestamp;
    }

    public void setRetrievalTimestamp(LocalDateTime retrievalTimestamp) {
        this.retrievalTimestamp = retrievalTimestamp;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JiraData jiraData = (JiraData) o;
        return Objects.equals(id, jiraData.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "JiraData{" +
                "id=" + id +
                ", boardId=" + boardId +
                ", teamId='" + teamId + '\'' +
                ", dataType='" + dataType + '\'' +
                ", retrievalTimestamp=" + retrievalTimestamp +
                ", recordCount=" + recordCount +
                ", createdAt=" + createdAt +
                '}';
    }

    /**
     * Creates a new JiraData instance with the specified parameters.
     *
     * @param boardId The board ID
     * @param teamId The team ID (can be null)
     * @param dataType The type of data
     * @param rawData The raw JSON data
     * @param retrievalTimestamp When the data was retrieved
     * @param recordCount Number of records in the data
     * @return A new JiraData instance
     */
    public static JiraData create(Long boardId, String teamId, String dataType,
                                String rawData, LocalDateTime retrievalTimestamp, Integer recordCount) {
        JiraData jiraData = new JiraData(boardId, dataType, rawData, retrievalTimestamp);
        jiraData.setTeamId(teamId);
        jiraData.setRecordCount(recordCount);
        return jiraData;
    }
}

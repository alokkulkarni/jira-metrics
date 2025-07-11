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
 * Entity representing a JIRA board configuration.
 * Stores board information and acts as configuration for the scheduled job.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Table("boards")
public class Board {

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

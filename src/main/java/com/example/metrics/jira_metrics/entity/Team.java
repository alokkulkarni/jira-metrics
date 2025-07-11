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
 * Entity representing a JIRA team.
 * Stores team information retrieved from JIRA API.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Table("teams")
public class Team {

    @Id
    @JsonProperty("id")
    private Long id;

    @NotNull
    @Column("team_id")
    @JsonProperty("teamId")
    private String teamId;

    @NotBlank
    @Column("team_name")
    @JsonProperty("teamName")
    private String teamName;

    @Column("description")
    @JsonProperty("description")
    private String description;

    @Column("lead_account_id")
    @JsonProperty("leadAccountId")
    private String leadAccountId;

    @Column("lead_display_name")
    @JsonProperty("leadDisplayName")
    private String leadDisplayName;

    @Column("member_count")
    @JsonProperty("memberCount")
    private Integer memberCount;

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
    public Team() {
    }

    /**
     * Constructor with required fields.
     *
     * @param teamId   The JIRA team ID
     * @param teamName The team name
     */
    public Team(String teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLeadAccountId() {
        return leadAccountId;
    }

    public void setLeadAccountId(String leadAccountId) {
        this.leadAccountId = leadAccountId;
    }

    public String getLeadDisplayName() {
        return leadDisplayName;
    }

    public void setLeadDisplayName(String leadDisplayName) {
        this.leadDisplayName = leadDisplayName;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
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
        Team team = (Team) o;
        return Objects.equals(teamId, team.teamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId);
    }

    @Override
    public String toString() {
        return "Team{" +
                "id=" + id +
                ", teamId='" + teamId + '\'' +
                ", teamName='" + teamName + '\'' +
                ", memberCount=" + memberCount +
                ", isActive=" + isActive +
                '}';
    }
}

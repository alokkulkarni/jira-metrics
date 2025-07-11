package com.example.metrics.jira_metrics.repository;

import com.example.metrics.jira_metrics.entity.Team;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Team entity operations.
 * Provides methods to manage team information retrieved from JIRA.
 */
@Repository
public interface TeamRepository extends CrudRepository<Team, Long> {

    /**
     * Finds a team by its JIRA team ID.
     *
     * @param teamId The JIRA team ID
     * @return Optional team entity
     */
    @Query("SELECT * FROM teams WHERE team_id = :teamId")
    Optional<Team> findByTeamId(@Param("teamId") String teamId);

    /**
     * Finds all active teams.
     *
     * @return List of active teams
     */
    @Query("SELECT * FROM teams WHERE is_active = true")
    List<Team> findAllActiveTeams();

    /**
     * Finds teams by name pattern (case-insensitive).
     *
     * @param teamName The team name pattern
     * @return List of matching teams
     */
    @Query("SELECT * FROM teams WHERE LOWER(team_name) LIKE LOWER(CONCAT('%', :teamName, '%')) AND is_active = true")
    List<Team> findByTeamNameContainingIgnoreCase(@Param("teamName") String teamName);
}

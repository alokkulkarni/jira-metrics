-- Test database initialization script for JIRA Metrics application
-- This script creates the necessary tables for integration tests

-- Drop tables if they exist (for clean test runs)
DROP TABLE IF EXISTS board_metrics CASCADE;
DROP TABLE IF EXISTS issues CASCADE;
DROP TABLE IF EXISTS sprints CASCADE;
DROP TABLE IF EXISTS board_details CASCADE;
DROP TABLE IF EXISTS jira_data CASCADE;
DROP TABLE IF EXISTS teams CASCADE;
DROP TABLE IF EXISTS boards CASCADE;

-- Create boards table
CREATE TABLE IF NOT EXISTS boards (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT NOT NULL UNIQUE,
    board_name VARCHAR(255) NOT NULL,
    project_key VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create teams table
CREATE TABLE IF NOT EXISTS teams (
    id BIGSERIAL PRIMARY KEY,
    team_id VARCHAR(255) NOT NULL UNIQUE,
    team_name VARCHAR(255) NOT NULL,
    description TEXT,
    lead_account_id VARCHAR(255),
    lead_display_name VARCHAR(255),
    member_count INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create jira_data table for storing raw JIRA responses
CREATE TABLE IF NOT EXISTS jira_data (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT,
    team_id VARCHAR(255),
    data_type VARCHAR(50) NOT NULL,
    raw_data TEXT,
    retrieval_timestamp TIMESTAMP NOT NULL,
    record_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create board_details table for enhanced board information
CREATE TABLE IF NOT EXISTS board_details (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT NOT NULL UNIQUE,
    board_type VARCHAR(50),
    board_location VARCHAR(255),
    filter_id BIGINT,
    can_edit BOOLEAN DEFAULT false,
    sub_query TEXT,
    column_config TEXT,
    estimation_config TEXT,
    ranking_config TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE
);

-- Create sprints table for sprint-specific data
CREATE TABLE IF NOT EXISTS sprints (
    id BIGSERIAL PRIMARY KEY,
    sprint_id BIGINT NOT NULL UNIQUE,
    board_id BIGINT NOT NULL,
    sprint_name VARCHAR(255) NOT NULL,
    sprint_state VARCHAR(50),
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    complete_date TIMESTAMP,
    goal TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE
);

-- Create issues table for issue-specific data
CREATE TABLE IF NOT EXISTS issues (
    id BIGSERIAL PRIMARY KEY,
    issue_id VARCHAR(50) NOT NULL UNIQUE,
    issue_key VARCHAR(50) NOT NULL,
    board_id BIGINT,
    sprint_id BIGINT,
    issue_type VARCHAR(100),
    status VARCHAR(100),
    priority VARCHAR(50),
    assignee_account_id VARCHAR(255),
    assignee_display_name VARCHAR(255),
    reporter_account_id VARCHAR(255),
    reporter_display_name VARCHAR(255),
    summary TEXT,
    description TEXT,
    story_points DECIMAL(5,2),
    original_estimate BIGINT,
    remaining_estimate BIGINT,
    time_spent BIGINT,
    created_date TIMESTAMP,
    updated_date TIMESTAMP,
    resolved_date TIMESTAMP,
    due_date TIMESTAMP,
    labels TEXT,
    components TEXT,
    fix_versions TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE SET NULL,
    FOREIGN KEY (sprint_id) REFERENCES sprints(sprint_id) ON DELETE SET NULL
);

-- Create board_metrics table for calculated insights
CREATE TABLE IF NOT EXISTS board_metrics (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT NOT NULL,
    sprint_id BIGINT,
    metric_period_start TIMESTAMP NOT NULL,
    metric_period_end TIMESTAMP NOT NULL,
    velocity_story_points DECIMAL(10,2) DEFAULT 0,
    velocity_issue_count INTEGER DEFAULT 0,
    planned_story_points DECIMAL(10,2) DEFAULT 0,
    completed_story_points DECIMAL(10,2) DEFAULT 0,
    defect_count INTEGER DEFAULT 0,
    defect_rate DECIMAL(5,4) DEFAULT 0,
    escaped_defects INTEGER DEFAULT 0,
    defect_density DECIMAL(8,4) DEFAULT 0,
    cycle_time_avg DECIMAL(10,2) DEFAULT 0,
    cycle_time_median DECIMAL(10,2) DEFAULT 0,
    lead_time_avg DECIMAL(10,2) DEFAULT 0,
    lead_time_median DECIMAL(10,2) DEFAULT 0,
    scope_change_count INTEGER DEFAULT 0,
    scope_churn_rate DECIMAL(5,4) DEFAULT 0,
    added_story_points DECIMAL(10,2) DEFAULT 0,
    removed_story_points DECIMAL(10,2) DEFAULT 0,
    commitment_reliability DECIMAL(5,4) DEFAULT 0,
    sprint_goal_success BOOLEAN DEFAULT false,
    throughput_issues INTEGER DEFAULT 0,
    throughput_story_points DECIMAL(10,2) DEFAULT 0,
    team_capacity_hours DECIMAL(8,2) DEFAULT 0,
    utilization_rate DECIMAL(5,4) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (sprint_id) REFERENCES sprints(sprint_id) ON DELETE SET NULL,
    UNIQUE(board_id, sprint_id, metric_period_start, metric_period_end)
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_boards_board_id ON boards(board_id);
CREATE INDEX IF NOT EXISTS idx_boards_is_active ON boards(is_active);
CREATE INDEX IF NOT EXISTS idx_teams_team_id ON teams(team_id);
CREATE INDEX IF NOT EXISTS idx_teams_is_active ON teams(is_active);
CREATE INDEX IF NOT EXISTS idx_jira_data_board_id ON jira_data(board_id);
CREATE INDEX IF NOT EXISTS idx_jira_data_team_id ON jira_data(team_id);
CREATE INDEX IF NOT EXISTS idx_jira_data_data_type ON jira_data(data_type);
CREATE INDEX IF NOT EXISTS idx_board_details_board_id ON board_details(board_id);
CREATE INDEX IF NOT EXISTS idx_sprints_board_id ON sprints(board_id);
CREATE INDEX IF NOT EXISTS idx_issues_board_id ON issues(board_id);
CREATE INDEX IF NOT EXISTS idx_issues_sprint_id ON issues(sprint_id);
CREATE INDEX IF NOT EXISTS idx_board_metrics_board_id ON board_metrics(board_id);
CREATE INDEX IF NOT EXISTS idx_board_metrics_sprint_id ON board_metrics(sprint_id);

-- Initial database schema for JIRA Metrics application
-- Creates core tables: boards, teams, jira_data
-- Author: JIRA Metrics Team
-- Date: 2025-07-11

-- Create boards table with all board configuration fields
CREATE TABLE boards (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT NOT NULL UNIQUE,
    board_name VARCHAR(255) NOT NULL,
    project_key VARCHAR(50) NOT NULL,
    board_type VARCHAR(50) DEFAULT 'simple', -- scrum, kanban, simple
    has_sprints BOOLEAN DEFAULT false,
    sprint_count INTEGER DEFAULT 0,

    -- Board configuration fields (formerly in board_details)
    board_location VARCHAR(255),
    filter_id BIGINT,
    can_edit BOOLEAN DEFAULT false,
    sub_query TEXT,
    column_config TEXT, -- JSON string of column configuration
    estimation_config TEXT, -- JSON string of estimation configuration
    ranking_config TEXT, -- JSON string of ranking configuration

    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create teams table
CREATE TABLE teams (
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
CREATE TABLE jira_data (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT,
    team_id VARCHAR(255),
    data_type VARCHAR(50) NOT NULL,
    raw_data TEXT,
    retrieval_timestamp TIMESTAMP NOT NULL,
    record_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create initial indexes for performance
CREATE INDEX idx_boards_board_id ON boards(board_id);
CREATE INDEX idx_boards_is_active ON boards(is_active);
CREATE INDEX idx_boards_board_type ON boards(board_type);
CREATE INDEX idx_teams_team_id ON teams(team_id);
CREATE INDEX idx_teams_is_active ON teams(is_active);
CREATE INDEX idx_jira_data_board_id ON jira_data(board_id);
CREATE INDEX idx_jira_data_team_id ON jira_data(team_id);
CREATE INDEX idx_jira_data_data_type ON jira_data(data_type);
CREATE INDEX idx_jira_data_retrieval_timestamp ON jira_data(retrieval_timestamp);

-- Create sprint and issue tables for JIRA Metrics application
-- Author: JIRA Metrics Team
-- Date: 2025-07-11

-- Create sprints table for sprint-specific data
CREATE TABLE sprints (
    id BIGSERIAL PRIMARY KEY,
    sprint_id BIGINT NOT NULL UNIQUE,
    board_id BIGINT NOT NULL,
    sprint_name VARCHAR(255) NOT NULL,
    sprint_state VARCHAR(50), -- future, active, closed
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    complete_date TIMESTAMP,
    goal TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE
);

-- Create issues table for issue-specific data
CREATE TABLE issues (
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
    original_estimate BIGINT, -- in seconds
    remaining_estimate BIGINT, -- in seconds
    time_spent BIGINT, -- in seconds
    created_date TIMESTAMP,
    updated_date TIMESTAMP,
    resolved_date TIMESTAMP,
    due_date TIMESTAMP,
    labels TEXT, -- JSON array as string
    components TEXT, -- JSON array as string
    fix_versions TEXT, -- JSON array as string
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE SET NULL,
    FOREIGN KEY (sprint_id) REFERENCES sprints(sprint_id) ON DELETE SET NULL
);

-- Create indexes for sprint and issue tables
CREATE INDEX idx_sprints_board_id ON sprints(board_id);
CREATE INDEX idx_sprints_sprint_state ON sprints(sprint_state);
CREATE INDEX idx_sprints_start_date ON sprints(start_date);
CREATE INDEX idx_sprints_end_date ON sprints(end_date);
CREATE INDEX idx_issues_board_id ON issues(board_id);
CREATE INDEX idx_issues_sprint_id ON issues(sprint_id);
CREATE INDEX idx_issues_issue_type ON issues(issue_type);
CREATE INDEX idx_issues_status ON issues(status);
CREATE INDEX idx_issues_assignee ON issues(assignee_account_id);
CREATE INDEX idx_issues_created_date ON issues(created_date);
CREATE INDEX idx_issues_resolved_date ON issues(resolved_date);

-- Schema enhancements for JIRA Metrics application
-- Adds indexes and constraints for performance optimization
-- No sample data - schema definition only
-- Author: JIRA Metrics Team
-- Date: 2025-07-15

-- Add performance indexes for frequently queried columns
CREATE INDEX IF NOT EXISTS idx_boards_project_key ON boards(project_key);
CREATE INDEX IF NOT EXISTS idx_boards_board_type ON boards(board_type);
CREATE INDEX IF NOT EXISTS idx_boards_is_active ON boards(is_active);

CREATE INDEX IF NOT EXISTS idx_sprints_board_id ON sprints(board_id);
CREATE INDEX IF NOT EXISTS idx_sprints_sprint_state ON sprints(sprint_state);
CREATE INDEX IF NOT EXISTS idx_sprints_start_date ON sprints(start_date);

CREATE INDEX IF NOT EXISTS idx_issues_board_id ON issues(board_id);
CREATE INDEX IF NOT EXISTS idx_issues_sprint_id ON issues(sprint_id);
CREATE INDEX IF NOT EXISTS idx_issues_status ON issues(status);
CREATE INDEX IF NOT EXISTS idx_issues_assignee_account_id ON issues(assignee_account_id);

CREATE INDEX IF NOT EXISTS idx_board_metrics_board_id ON board_metrics(board_id);
CREATE INDEX IF NOT EXISTS idx_board_metrics_period_start ON board_metrics(metric_period_start);
CREATE INDEX IF NOT EXISTS idx_board_metrics_metric_type ON board_metrics(metric_type);

CREATE INDEX IF NOT EXISTS idx_teams_is_active ON teams(is_active);

CREATE INDEX IF NOT EXISTS idx_jira_data_board_id ON jira_data(board_id);
CREATE INDEX IF NOT EXISTS idx_jira_data_data_type ON jira_data(data_type);
CREATE INDEX IF NOT EXISTS idx_jira_data_retrieval_timestamp ON jira_data(retrieval_timestamp);

-- Add composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_sprints_board_state ON sprints(board_id, sprint_state);
CREATE INDEX IF NOT EXISTS idx_issues_board_status ON issues(board_id, status);
CREATE INDEX IF NOT EXISTS idx_board_metrics_board_period ON board_metrics(board_id, metric_period_start, metric_period_end);

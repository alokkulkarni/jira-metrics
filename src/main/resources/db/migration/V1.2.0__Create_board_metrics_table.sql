-- Create board metrics table for calculated insights
-- Supports both sprint-based and issue-based metrics
-- Author: JIRA Metrics Team
-- Date: 2025-07-11

CREATE TABLE board_metrics (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT NOT NULL,
    sprint_id BIGINT,
    metric_period_start TIMESTAMP NOT NULL,
    metric_period_end TIMESTAMP NOT NULL,
    metric_type VARCHAR(20) NOT NULL DEFAULT 'SPRINT_BASED', -- SPRINT_BASED or ISSUE_BASED
    board_type VARCHAR(50), -- scrum, kanban, simple

    -- Velocity metrics
    velocity_story_points DECIMAL(10,2) DEFAULT 0,
    velocity_issue_count INTEGER DEFAULT 0,
    planned_story_points DECIMAL(10,2) DEFAULT 0,
    completed_story_points DECIMAL(10,2) DEFAULT 0,

    -- Quality metrics
    defect_count INTEGER DEFAULT 0,
    defect_rate DECIMAL(5,4) DEFAULT 0, -- percentage as decimal
    escaped_defects INTEGER DEFAULT 0,
    defect_density DECIMAL(8,4) DEFAULT 0, -- defects per story point

    -- Flow metrics
    cycle_time_avg DECIMAL(10,2) DEFAULT 0, -- average in days
    cycle_time_median DECIMAL(10,2) DEFAULT 0,
    lead_time_avg DECIMAL(10,2) DEFAULT 0,
    lead_time_median DECIMAL(10,2) DEFAULT 0,

    -- Churn metrics (applicable mainly for sprint-based)
    scope_change_count INTEGER DEFAULT 0,
    scope_churn_rate DECIMAL(5,4) DEFAULT 0, -- percentage as decimal
    added_story_points DECIMAL(10,2) DEFAULT 0,
    removed_story_points DECIMAL(10,2) DEFAULT 0,

    -- Predictability metrics
    commitment_reliability DECIMAL(5,4) DEFAULT 0, -- percentage as decimal
    sprint_goal_success BOOLEAN DEFAULT false,

    -- Throughput metrics
    throughput_issues INTEGER DEFAULT 0,
    throughput_story_points DECIMAL(10,2) DEFAULT 0,

    -- Team metrics
    team_capacity_hours DECIMAL(8,2) DEFAULT 0,
    team_utilization DECIMAL(5,4) DEFAULT 0, -- percentage as decimal
    team_focus_factor DECIMAL(5,4) DEFAULT 0, -- percentage as decimal

    -- Issue-based specific metrics
    issues_in_progress INTEGER DEFAULT 0,
    issues_in_backlog INTEGER DEFAULT 0,
    issues_done INTEGER DEFAULT 0,
    wip_limit_adherence DECIMAL(5,4) DEFAULT 0, -- percentage as decimal

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (sprint_id) REFERENCES sprints(sprint_id) ON DELETE SET NULL
);

-- Create indexes for board metrics
CREATE INDEX idx_board_metrics_board_id ON board_metrics(board_id);
CREATE INDEX idx_board_metrics_sprint_id ON board_metrics(sprint_id);
CREATE INDEX idx_board_metrics_period ON board_metrics(metric_period_start, metric_period_end);
CREATE INDEX idx_board_metrics_metric_type ON board_metrics(metric_type);
CREATE INDEX idx_board_metrics_board_type ON board_metrics(board_type);

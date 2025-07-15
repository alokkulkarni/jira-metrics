-- Board validation enhancement migration
-- Adds additional indexes and constraints for board validation features
-- Author: JIRA Metrics Team
-- Date: 2025-07-11

-- Add additional indexes for board validation performance
CREATE INDEX idx_boards_has_sprints ON boards(has_sprints);
CREATE INDEX idx_boards_sprint_count ON boards(sprint_count);

-- Add check constraints for data integrity
ALTER TABLE boards ADD CONSTRAINT chk_boards_sprint_count_non_negative
    CHECK (sprint_count >= 0);

ALTER TABLE board_metrics ADD CONSTRAINT chk_board_metrics_metric_type
    CHECK (metric_type IN ('SPRINT_BASED', 'ISSUE_BASED'));

ALTER TABLE board_metrics ADD CONSTRAINT chk_board_metrics_board_type
    CHECK (board_type IN ('scrum', 'kanban', 'simple'));

-- Add comments for better documentation
COMMENT ON TABLE boards IS 'JIRA boards with configuration and validation information';
COMMENT ON TABLE sprints IS 'JIRA sprint data for agile boards';
COMMENT ON TABLE issues IS 'JIRA issues with comprehensive tracking information';
COMMENT ON TABLE board_metrics IS 'Calculated metrics supporting both sprint-based and issue-based analysis';

COMMENT ON COLUMN board_metrics.cycle_time_avg IS 'Average cycle time in days';
COMMENT ON COLUMN board_metrics.cycle_time_median IS 'Median cycle time in days';
COMMENT ON COLUMN board_metrics.lead_time_avg IS 'Average lead time in days';
COMMENT ON COLUMN board_metrics.lead_time_median IS 'Median lead time in days';
COMMENT ON COLUMN board_metrics.metric_type IS 'Type of metrics calculation: SPRINT_BASED or ISSUE_BASED';

-- Create a view for active boards with sprint information
CREATE VIEW v_active_boards_with_sprints AS
SELECT
    b.board_id,
    b.board_name,
    b.project_key,
    b.board_type,
    b.has_sprints,
    b.sprint_count,
    b.is_active,
    COUNT(s.sprint_id) AS actual_sprint_count,
    MAX(s.start_date) AS last_sprint_start,
    MAX(s.end_date) AS last_sprint_end
FROM boards b
LEFT JOIN sprints s ON b.board_id = s.board_id
WHERE b.is_active = true
GROUP BY b.board_id, b.board_name, b.project_key, b.board_type,
         b.has_sprints, b.sprint_count, b.is_active;

-- Migration script to consolidate board_details into boards table and add validation support
-- Run this on existing databases to consolidate tables and add new columns

-- First, add new columns to boards table for sprint validation and board details consolidation
ALTER TABLE boards
ADD COLUMN IF NOT EXISTS board_type VARCHAR(50) DEFAULT 'simple',
ADD COLUMN IF NOT EXISTS has_sprints BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS sprint_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS board_location VARCHAR(255),
ADD COLUMN IF NOT EXISTS filter_id BIGINT,
ADD COLUMN IF NOT EXISTS can_edit BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS sub_query TEXT,
ADD COLUMN IF NOT EXISTS column_config TEXT,
ADD COLUMN IF NOT EXISTS estimation_config TEXT,
ADD COLUMN IF NOT EXISTS ranking_config TEXT;

-- Migrate data from board_details to boards table (if board_details exists)
UPDATE boards
SET board_type = COALESCE(bd.board_type, 'simple'),
    board_location = bd.board_location,
    filter_id = bd.filter_id,
    can_edit = COALESCE(bd.can_edit, false),
    sub_query = bd.sub_query,
    column_config = bd.column_config,
    estimation_config = bd.estimation_config,
    ranking_config = bd.ranking_config
FROM board_details bd
WHERE boards.board_id = bd.board_id
AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'board_details');

-- Update boards to detect if they have sprints
UPDATE boards
SET has_sprints = CASE
    WHEN (SELECT COUNT(*) FROM sprints WHERE sprints.board_id = boards.board_id) > 0 THEN true
    ELSE false
END,
sprint_count = (
    SELECT COUNT(*)
    FROM sprints
    WHERE sprints.board_id = boards.board_id
),
board_type = CASE
    WHEN board_type IS NULL OR board_type = 'simple' THEN
        CASE
            WHEN (SELECT COUNT(*) FROM sprints WHERE sprints.board_id = boards.board_id) > 0 THEN 'scrum'
            ELSE 'kanban'
        END
    ELSE board_type
END;

-- Add new columns to board_metrics table for metrics type differentiation
ALTER TABLE board_metrics
ADD COLUMN IF NOT EXISTS metric_type VARCHAR(20) NOT NULL DEFAULT 'SPRINT_BASED',
ADD COLUMN IF NOT EXISTS board_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS team_utilization DECIMAL(5,4) DEFAULT 0,
ADD COLUMN IF NOT EXISTS team_focus_factor DECIMAL(5,4) DEFAULT 0,
ADD COLUMN IF NOT EXISTS issues_in_progress INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS issues_in_backlog INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS issues_done INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS wip_limit_adherence DECIMAL(5,4) DEFAULT 0;

-- Update existing board_metrics to include metric type
UPDATE board_metrics
SET metric_type = CASE
    WHEN sprint_id IS NOT NULL THEN 'SPRINT_BASED'
    ELSE 'ISSUE_BASED'
END,
board_type = (
    SELECT board_type
    FROM boards
    WHERE boards.board_id = board_metrics.board_id
)
WHERE metric_type IS NULL OR board_type IS NULL;

-- Create additional indexes for the new columns
CREATE INDEX IF NOT EXISTS idx_boards_board_type ON boards(board_type);
CREATE INDEX IF NOT EXISTS idx_boards_has_sprints ON boards(has_sprints);
CREATE INDEX IF NOT EXISTS idx_board_metrics_metric_type ON board_metrics(metric_type);
CREATE INDEX IF NOT EXISTS idx_board_metrics_board_type ON board_metrics(board_type);

-- Drop the board_details table if it exists (after data migration)
DROP TABLE IF EXISTS board_details CASCADE;

-- Update cycle time and lead time column comments
COMMENT ON COLUMN board_metrics.cycle_time_avg IS 'Average cycle time in days';
COMMENT ON COLUMN board_metrics.cycle_time_median IS 'Median cycle time in days';
COMMENT ON COLUMN board_metrics.lead_time_avg IS 'Average lead time in days';
COMMENT ON COLUMN board_metrics.lead_time_median IS 'Median lead time in days';

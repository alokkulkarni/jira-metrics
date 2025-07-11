package com.example.metrics.jira_metrics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduled job for retrieving JIRA data from various boards.
 * Runs every two minutes and processes all active boards and team data.
 */
@Component
public class JiraDataScheduledJob {

    private static final Logger logger = LoggerFactory.getLogger(JiraDataScheduledJob.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JiraDataService jiraDataService;

    /**
     * Constructor for JiraDataScheduledJob.
     *
     * @param jiraDataService The JIRA data service
     */
    public JiraDataScheduledJob(JiraDataService jiraDataService) {
        this.jiraDataService = jiraDataService;
    }

    /**
     * Scheduled method that runs every 2 minutes to retrieve JIRA data.
     * Cron expression means every 2 minutes starting from minute 0.
     */
//    @Scheduled(cron = "0 */5 * * * *")    // Every 5 minutes
//    @Scheduled(cron = "0 */10 * * * *")   // Every 10 minutes
//    @Scheduled(cron = "0 */15 * * * *")   // Every 15 minutes
//    @Scheduled(cron = "0 0/30 * * * *")   // Every 30 minutes
//    @Scheduled(cron = "0 0 */2 * * *")
    @Scheduled(cron = "0 */2 * * * *")
    public void retrieveJiraData() {
        LocalDateTime startTime = LocalDateTime.now();
        logger.info("Starting scheduled JIRA data retrieval job at {}",
                   startTime.format(FORMATTER));

        // Process team data first to ensure teams are up to date
        try {
            logger.info("Processing team data from JIRA");
            jiraDataService.processTeamData();
        } catch (Exception e) {
            logger.error("Error processing team data: {}", e.getMessage(), e);
        }

        // Process all board data
        try {
            logger.info("Processing data for all active boards");
            jiraDataService.processAllBoards();
        } catch (Exception e) {
            logger.error("Error processing board data: {}", e.getMessage(), e);
        }

        LocalDateTime endTime = LocalDateTime.now();
        long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();

        logger.info("Completed scheduled JIRA data retrieval job at {}. Duration: {} seconds",
                   endTime.format(FORMATTER), durationSeconds);
    }

    /**
     * Manual trigger for JIRA data retrieval (useful for testing or manual runs).
     * This method can be called programmatically when needed.
     */
    public void triggerManualDataRetrieval() {
        logger.info("Manual JIRA data retrieval triggered");
        retrieveJiraData();
    }

    /**
     * Health check method to verify the scheduled job is properly configured.
     * Logs the next scheduled execution time information.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void logSchedulerHealth() {
        logger.info("JIRA Data Scheduler is active. Next data retrieval will occur at the next 2-hour interval.");
    }
}

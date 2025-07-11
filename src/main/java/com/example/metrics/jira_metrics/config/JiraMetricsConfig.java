package com.example.metrics.jira_metrics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

/**
 * Configuration class for JIRA Metrics application.
 * Provides beans for JSON processing, async task execution, and transaction management.
 */
@Configuration
@EnableAsync
@EnableTransactionManagement
public class JiraMetricsConfig {

    /**
     * Creates an ObjectMapper bean for JSON processing.
     * Configured to handle Java time types and snake_case naming.
     *
     * @return Configured ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return mapper;
    }

    /**
     * Creates a task executor for async operations.
     * Used for non-blocking JIRA API calls when needed.
     *
     * @return Configured ThreadPoolTaskExecutor
     */
    @Bean(name = "jiraTaskExecutor")
    public Executor jiraTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("jira-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Creates the primary transaction manager for the application.
     * This resolves the conflict between Spring Cloud Task's transaction manager
     * and the default transaction manager.
     *
     * @param dataSource The application's data source
     * @return Configured DataSourceTransactionManager marked as primary
     */
    @Bean
    @Primary
    @Qualifier("transactionManager")
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}

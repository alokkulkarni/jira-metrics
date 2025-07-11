package com.example.metrics.jira_metrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JiraMetricsApplication {

	public static void main(String[] args) {
		SpringApplication.run(JiraMetricsApplication.class, args);
	}

}

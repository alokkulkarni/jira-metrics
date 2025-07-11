package unit.com.example.metrics.jira_metrics.service;

import com.example.metrics.jira_metrics.service.JiraDataScheduledJob;
import com.example.metrics.jira_metrics.service.JiraDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Unit tests for JiraDataScheduledJob.
 * Tests scheduled job execution and error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JiraDataScheduledJob Unit Tests")
class JiraDataScheduledJobTest {

    @Mock
    private JiraDataService jiraDataService;

    @InjectMocks
    private JiraDataScheduledJob jiraDataScheduledJob;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(jiraDataService);
    }

    @Test
    @DisplayName("Should execute scheduled job successfully")
    void retrieveJiraData_WithValidService_ShouldExecuteSuccessfully() {
        // Given - mocks are configured to not throw exceptions

        // When
        jiraDataScheduledJob.retrieveJiraData();

        // Then
        verify(jiraDataService).processTeamData();
        verify(jiraDataService).processAllBoards();
    }

    @Test
    @DisplayName("Should handle team data processing exception gracefully")
    void retrieveJiraData_WithTeamDataException_ShouldContinueProcessing() {
        // Given
        doThrow(new RuntimeException("Team data error")).when(jiraDataService).processTeamData();

        // When
        jiraDataScheduledJob.retrieveJiraData();

        // Then
        verify(jiraDataService).processTeamData();
        verify(jiraDataService).processAllBoards();
    }

    @Test
    @DisplayName("Should handle board data processing exception gracefully")
    void retrieveJiraData_WithBoardDataException_ShouldHandleGracefully() {
        // Given
        doThrow(new RuntimeException("Board data error")).when(jiraDataService).processAllBoards();

        // When
        jiraDataScheduledJob.retrieveJiraData();

        // Then
        verify(jiraDataService).processTeamData();
        verify(jiraDataService).processAllBoards();
    }

    @Test
    @DisplayName("Should execute manual trigger successfully")
    void triggerManualDataRetrieval_ShouldCallRetrieveJiraData() {
        // When
        jiraDataScheduledJob.triggerManualDataRetrieval();

        // Then
        verify(jiraDataService).processTeamData();
        verify(jiraDataService).processAllBoards();
    }
}

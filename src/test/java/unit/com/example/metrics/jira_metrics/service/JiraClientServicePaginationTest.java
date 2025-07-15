package com.example.metrics.jira_metrics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JiraClientService pagination functionality.
 * Validates that the service correctly handles JIRA API pagination for board retrieval.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class JiraClientServicePaginationTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private JiraClientService jiraClientService;

    private static final String TEST_JIRA_BASE_URL = "https://test.atlassian.net";
    private static final String TEST_USERNAME = "test@example.com";
    private static final String TEST_API_TOKEN = "test-api-token";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Create service instance with mocked RestTemplate
        jiraClientService = new JiraClientService(
            TEST_JIRA_BASE_URL,
            TEST_USERNAME,
            TEST_API_TOKEN,
            objectMapper
        );

        // Use reflection to inject mocked RestTemplate
        try {
            var restTemplateField = JiraClientService.class.getDeclaredField("restTemplate");
            restTemplateField.setAccessible(true);
            restTemplateField.set(jiraClientService, restTemplate);
        } catch (Exception e) {
            fail("Failed to inject mocked RestTemplate: " + e.getMessage());
        }
    }

    /**
     * Test that getAllBoards retrieves all boards across multiple pages.
     * Simulates JIRA API returning 3 pages with different numbers of boards.
     */
    @Test
    void shouldRetrieveAllBoardsAcrossMultiplePages() throws Exception {
        // Given: Mock responses for 3 pages of boards
        String page1Response = createBoardPageResponse(0, 50, 125, false,
            generateBoardsJson(1, 50));
        String page2Response = createBoardPageResponse(50, 50, 125, false,
            generateBoardsJson(51, 100));
        String page3Response = createBoardPageResponse(100, 25, 125, true,
            generateBoardsJson(101, 125));

        // Mock REST calls for each page
        when(restTemplate.exchange(
            eq(TEST_JIRA_BASE_URL + "/rest/agile/1.0/board?startAt=0&maxResults=50"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(page1Response, HttpStatus.OK));

        when(restTemplate.exchange(
            eq(TEST_JIRA_BASE_URL + "/rest/agile/1.0/board?startAt=50&maxResults=50"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(page2Response, HttpStatus.OK));

        when(restTemplate.exchange(
            eq(TEST_JIRA_BASE_URL + "/rest/agile/1.0/board?startAt=100&maxResults=50"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(page3Response, HttpStatus.OK));

        // When: Get all boards
        Optional<JsonNode> result = jiraClientService.getAllBoards();

        // Then: Verify all boards are retrieved
        assertTrue(result.isPresent(), "Should return boards");
        JsonNode allBoards = result.get();

        JsonNode values = allBoards.path("values");
        assertEquals(125, values.size(), "Should retrieve all 125 boards");
        assertEquals(125, allBoards.path("total").asInt(), "Total should be 125");
        assertTrue(allBoards.path("isLast").asBoolean(), "Consolidated response should be marked as last");

        // Verify correct number of API calls
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    /**
     * Test that getAllBoards handles single page correctly.
     */
    @Test
    void shouldHandleSinglePageCorrectly() throws Exception {
        // Given: Single page with 30 boards
        String pageResponse = createBoardPageResponse(0, 30, 30, true,
            generateBoardsJson(1, 30));

        when(restTemplate.exchange(
            eq(TEST_JIRA_BASE_URL + "/rest/agile/1.0/board?startAt=0&maxResults=50"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(pageResponse, HttpStatus.OK));

        // When: Get all boards
        Optional<JsonNode> result = jiraClientService.getAllBoards();

        // Then: Verify single page is handled correctly
        assertTrue(result.isPresent(), "Should return boards");
        JsonNode allBoards = result.get();

        assertEquals(30, allBoards.path("values").size(), "Should retrieve all 30 boards");
        assertEquals(30, allBoards.path("total").asInt(), "Total should be 30");

        // Verify only one API call
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    /**
     * Test that getAllBoards handles empty response correctly.
     */
    @Test
    void shouldHandleEmptyResponseCorrectly() throws Exception {
        // Given: Empty response
        String emptyResponse = createBoardPageResponse(0, 0, 0, true, "[]");

        when(restTemplate.exchange(
            eq(TEST_JIRA_BASE_URL + "/rest/agile/1.0/board?startAt=0&maxResults=50"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(emptyResponse, HttpStatus.OK));

        // When: Get all boards
        Optional<JsonNode> result = jiraClientService.getAllBoards();

        // Then: Verify empty response is handled correctly
        assertTrue(result.isPresent(), "Should return empty boards collection");
        JsonNode allBoards = result.get();

        assertEquals(0, allBoards.path("values").size(), "Should have no boards");
        assertEquals(0, allBoards.path("total").asInt(), "Total should be 0");

        // Verify only one API call
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    /**
     * Test that getAllBoards respects safety limit to prevent infinite loops.
     */
    @Test
    void shouldRespectSafetyLimit() throws Exception {
        // Given: Mock responses that would create infinite pagination
        String infinitePage = createBoardPageResponse(0, 50, 999999, false,
            generateBoardsJson(1, 50));

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(infinitePage, HttpStatus.OK));

        // When: Get all boards
        Optional<JsonNode> result = jiraClientService.getAllBoards();

        // Then: Verify safety limit is respected
        assertTrue(result.isPresent(), "Should return boards despite hitting limit");
        JsonNode allBoards = result.get();

        assertTrue(allBoards.path("values").size() <= 10000, "Should not exceed safety limit");

        // Verify maximum API calls limit is respected (200 pages max)
        verify(restTemplate, atMost(200)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    /**
     * Test that getAllBoards handles API errors gracefully.
     */
    @Test
    void shouldHandleApiErrorsGracefully() {
        // Given: API call that throws exception
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenThrow(new RuntimeException("API Error"));

        // When: Get all boards
        Optional<JsonNode> result = jiraClientService.getAllBoards();

        // Then: Verify error is handled gracefully
        assertFalse(result.isPresent(), "Should return empty Optional on error");
    }

    /**
     * Test that getAllBoards stops pagination when a page fails to load.
     */
    @Test
    void shouldStopPaginationOnPageFailure() throws Exception {
        // Given: First page succeeds, second page fails
        String page1Response = createBoardPageResponse(0, 50, 100, false,
            generateBoardsJson(1, 50));

        when(restTemplate.exchange(
            eq(TEST_JIRA_BASE_URL + "/rest/agile/1.0/board?startAt=0&maxResults=50"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(page1Response, HttpStatus.OK));

        when(restTemplate.exchange(
            eq(TEST_JIRA_BASE_URL + "/rest/agile/1.0/board?startAt=50&maxResults=50"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenThrow(new RuntimeException("Second page error"));

        // When: Get all boards
        Optional<JsonNode> result = jiraClientService.getAllBoards();

        // Then: Verify pagination stops and returns first page results
        assertTrue(result.isPresent(), "Should return boards from successful pages");
        JsonNode allBoards = result.get();

        assertEquals(50, allBoards.path("values").size(), "Should return boards from first page only");

        // Verify both API calls were attempted
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    /**
     * Creates a mock JSON response for a page of boards.
     */
    private String createBoardPageResponse(int startAt, int maxResults, int total, boolean isLast, String valuesJson) {
        return String.format("""
            {
                "startAt": %d,
                "maxResults": %d,
                "total": %d,
                "isLast": %s,
                "values": %s
            }
            """, startAt, maxResults, total, isLast, valuesJson);
    }

    /**
     * Generates JSON for a range of mock boards.
     */
    private String generateBoardsJson(int startId, int endId) {
        StringBuilder boards = new StringBuilder("[");
        for (int i = startId; i <= endId; i++) {
            if (i > startId) {
                boards.append(",");
            }
            boards.append(String.format("""
                {
                    "id": %d,
                    "name": "Test Board %d",
                    "type": "scrum",
                    "location": {
                        "projectKey": "TEST%d",
                        "projectName": "Test Project %d"
                    }
                }
                """, i, i, i % 10, i % 10));
        }
        boards.append("]");
        return boards.toString();
    }
}

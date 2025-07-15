package com.example.metrics.jira_metrics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for communicating with JIRA REST API.
 * Handles authentication and data retrieval from various JIRA endpoints.
 * Supports both board ID lookups and project key/board name based operations.
 *
 * @author JIRA Metrics Team
 * @since 1.0.0
 */
@Service
public class JiraClientService {

    private static final Logger logger = LoggerFactory.getLogger(JiraClientService.class);
    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String jiraBaseUrl;
    private final HttpHeaders defaultHeaders;

    // Cache for board ID lookups to avoid repeated API calls
    private final Map<String, Long> boardIdCache = new ConcurrentHashMap<>();
    private final Map<String, Long> projectKeyToBoardIdCache = new ConcurrentHashMap<>();

    // Constants for pagination
    private static final int MAX_RESULTS_PER_PAGE = 50; // JIRA default maximum
    private static final int MAX_BOARDS_LIMIT = 10000; // Safety limit to prevent infinite loops

    /**
     * Constructor for JiraClientService.
     *
     * @param jiraBaseUrl JIRA base URL from configuration
     * @param jiraUsername JIRA username for authentication
     * @param jiraApiToken JIRA API token for authentication
     * @param objectMapper JSON object mapper
     */
    public JiraClientService(@Value("${jira.base-url}") String jiraBaseUrl,
                           @Value("${jira.username}") String jiraUsername,
                           @Value("${jira.api-token}") String jiraApiToken,
                           ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jiraBaseUrl = jiraBaseUrl;

        // Log configuration (without sensitive data)
        logger.info("Initializing JiraClientService with URL: {} and username: {}", jiraBaseUrl, jiraUsername);

        // Validate credentials
        if (jiraApiToken == null || jiraApiToken.trim().isEmpty() || jiraApiToken.startsWith("${")) {
            logger.error("JIRA API token is not properly configured. Please set JIRA_API_TOKEN environment variable.");
            throw new IllegalArgumentException("JIRA API token is required");
        }

        if (jiraUsername == null || jiraUsername.trim().isEmpty()) {
            logger.error("JIRA username is not properly configured.");
            throw new IllegalArgumentException("JIRA username is required");
        }

        // Create RestTemplate with timeouts
        this.restTemplate = new RestTemplate();

        // Set up default headers with authentication
        this.defaultHeaders = new HttpHeaders();
        this.defaultHeaders.setContentType(MediaType.APPLICATION_JSON);
        this.defaultHeaders.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        String authHeader = createBasicAuthHeader(jiraUsername, jiraApiToken);
        this.defaultHeaders.set(HttpHeaders.AUTHORIZATION, authHeader);

        // Log auth header format (without exposing credentials)
        logger.info("Authentication header configured for user: {}", jiraUsername);
        logger.debug("Auth header starts with: {}", authHeader.substring(0, Math.min(10, authHeader.length())));

        logger.info("JiraClientService initialized successfully");
    }

    /**
     * Retrieves all boards from JIRA with pagination support.
     * Handles JIRA's 50-record limit by making multiple API calls to fetch all boards.
     *
     * @return Optional JSON response containing all boards consolidated from all pages
     */
    public Optional<JsonNode> getAllBoards() {
        try {
            logger.debug("Fetching all boards from JIRA with pagination support");

            var allBoards = new java.util.ArrayList<JsonNode>();
            int startAt = 0;
            int totalBoardsRetrieved = 0;
            boolean hasMorePages = true;
            int pageCount = 0;

            while (hasMorePages && totalBoardsRetrieved < MAX_BOARDS_LIMIT) {
                pageCount++;
                logger.debug("Fetching boards page {} (starting at index {})", pageCount, startAt);

                Optional<JsonNode> pageResponse = fetchBoardsPage(startAt, MAX_RESULTS_PER_PAGE);

                if (pageResponse.isEmpty()) {
                    logger.warn("Failed to fetch boards page {} starting at index {}", pageCount, startAt);
                    break;
                }

                JsonNode page = pageResponse.get();
                JsonNode values = page.path("values");
                int currentPageSize = values.size();

                // Add boards from current page to our collection
                for (JsonNode board : values) {
                    allBoards.add(board);
                }

                totalBoardsRetrieved += currentPageSize;

                // Check pagination metadata
                int total = page.path("total").asInt(-1);
                boolean isLast = page.path("isLast").asBoolean(true);

                logger.debug("Page {}: retrieved {} boards, total so far: {}, page marked as last: {}",
                           pageCount, currentPageSize, totalBoardsRetrieved, isLast);

                // Determine if there are more pages
                if (isLast || currentPageSize == 0 || (total > 0 && totalBoardsRetrieved >= total)) {
                    hasMorePages = false;
                } else {
                    startAt += currentPageSize;
                }

                // Safety check to prevent infinite loops
                if (pageCount > 200) { // Max 200 pages = 10,000 boards
                    logger.warn("Reached maximum page limit (200), stopping pagination");
                    break;
                }
            }

            logger.info("Successfully retrieved {} boards from {} pages", totalBoardsRetrieved, pageCount);

            // Create consolidated response
            var consolidatedResponse = objectMapper.createObjectNode();
            consolidatedResponse.put("startAt", 0);
            consolidatedResponse.put("maxResults", totalBoardsRetrieved);
            consolidatedResponse.put("total", totalBoardsRetrieved);
            consolidatedResponse.put("isLast", true);
            consolidatedResponse.set("values", objectMapper.valueToTree(allBoards));

            return Optional.of(consolidatedResponse);

        } catch (RestClientException e) {
            handleRestClientException(e, "fetching all boards with pagination");
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error fetching all boards with pagination", e);
            return Optional.empty();
        }
    }

    /**
     * Fetches a single page of boards from JIRA API.
     *
     * @param startAt starting index for pagination
     * @param maxResults maximum number of results per page
     * @return Optional JSON response for the requested page
     */
    private Optional<JsonNode> fetchBoardsPage(int startAt, int maxResults) {
        try {
            String endpoint = String.format("/rest/agile/1.0/board?startAt=%d&maxResults=%d",
                                          startAt, maxResults);

            logger.debug("Fetching boards page from endpoint: {}", endpoint);

            HttpEntity<String> requestEntity = new HttpEntity<>(defaultHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                jiraBaseUrl + endpoint,
                HttpMethod.GET,
                requestEntity,
                String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            int pageSize = jsonNode.path("values").size();
            int total = jsonNode.path("total").asInt(-1);
            boolean isLast = jsonNode.path("isLast").asBoolean(true);

            logger.debug("Retrieved page with {} boards (total: {}, isLast: {})",
                        pageSize, total, isLast);

            return Optional.of(jsonNode);

        } catch (RestClientException e) {
            logger.error("REST client error fetching boards page at startAt={}: {}",
                        startAt, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error fetching boards page at startAt={}", startAt, e);
            return Optional.empty();
        }
    }

    /**
     * Finds board ID by project key using cached lookup or API call.
     *
     * @param projectKey The project key to search for
     * @return Optional board ID if found
     */
    public Optional<Long> findBoardIdByProjectKey(String projectKey) {
        if (projectKey == null || projectKey.trim().isEmpty()) {
            logger.warn("Project key is null or empty");
            return Optional.empty();
        }

        // Check cache first
        Long cachedBoardId = projectKeyToBoardIdCache.get(projectKey);
        if (cachedBoardId != null) {
            logger.debug("Found cached board ID {} for project key: {}", cachedBoardId, projectKey);
            return Optional.of(cachedBoardId);
        }

        logger.debug("Searching for board with project key: {}", projectKey);

        // Search through all boards
        Optional<JsonNode> allBoards = getAllBoards();
        if (allBoards.isPresent()) {
            JsonNode boards = allBoards.get().path("values");
            logger.debug("Found {} boards to search through", boards.size());

            for (JsonNode board : boards) {
                JsonNode location = board.path("location");
                String boardProjectKey = location.path("projectKey").asText("");
                String boardName = board.path("name").asText("");
                Long boardId = board.path("id").asLong();

                logger.debug("Checking board: ID={}, name='{}', projectKey='{}'",
                           boardId, boardName, boardProjectKey);

                if (projectKey.equalsIgnoreCase(boardProjectKey)) {
                    // Cache the result
                    projectKeyToBoardIdCache.put(projectKey, boardId);
                    logger.info("Found board ID {} for project key: {}", boardId, projectKey);
                    return Optional.of(boardId);
                }
            }
        }

        logger.warn("No board found for project key: {}", projectKey);
        return Optional.empty();
    }

    /**
     * Finds board ID by board name using cached lookup or API call.
     *
     * @param boardName The board name to search for
     * @return Optional board ID if found
     */
    public Optional<Long> findBoardIdByName(String boardName) {
        if (boardName == null || boardName.trim().isEmpty()) {
            logger.warn("Board name is null or empty");
            return Optional.empty();
        }

        // Check cache first
        Long cachedBoardId = boardIdCache.get(boardName);
        if (cachedBoardId != null) {
            logger.debug("Found cached board ID {} for board name: {}", cachedBoardId, boardName);
            return Optional.of(cachedBoardId);
        }

        // Search through all boards
        Optional<JsonNode> allBoards = getAllBoards();
        if (allBoards.isPresent()) {
            JsonNode boards = allBoards.get().path("values");

            for (JsonNode board : boards) {
                String currentBoardName = board.path("name").asText("");

                if (boardName.equalsIgnoreCase(currentBoardName)) {
                    Long boardId = board.path("id").asLong();
                    // Cache the result
                    boardIdCache.put(boardName, boardId);
                    logger.debug("Found board ID {} for board name: {}", boardId, boardName);
                    return Optional.of(boardId);
                }
            }
        }

        logger.warn("No board found for board name: {}", boardName);
        return Optional.empty();
    }

    /**
     * Retrieves board configuration by project key.
     *
     * @param projectKey The project key
     * @return Optional JSON response containing board configuration
     */
    public Optional<JsonNode> getBoardConfigurationByProjectKey(String projectKey) {
        Optional<Long> boardId = findBoardIdByProjectKey(projectKey);
        if (boardId.isEmpty()) {
            logger.warn("Cannot retrieve board configuration - no board found for project key: {}", projectKey);
            return Optional.empty();
        }

        return getBoardConfiguration(boardId.get());
    }

    /**
     * Retrieves board configuration by board name.
     *
     * @param boardName The board name
     * @return Optional JSON response containing board configuration
     */
    public Optional<JsonNode> getBoardConfigurationByName(String boardName) {
        Optional<Long> boardId = findBoardIdByName(boardName);
        if (boardId.isEmpty()) {
            logger.warn("Cannot retrieve board configuration - no board found for board name: {}", boardName);
            return Optional.empty();
        }

        return getBoardConfiguration(boardId.get());
    }

    /**
     * Retrieves board sprints by project key.
     *
     * @param projectKey The project key
     * @return Optional JSON response containing board sprints
     */
    public Optional<JsonNode> getBoardSprintsByProjectKey(String projectKey) {
        Optional<Long> boardId = findBoardIdByProjectKey(projectKey);
        if (boardId.isEmpty()) {
            logger.warn("Cannot retrieve board sprints - no board found for project key: {}", projectKey);
            return Optional.empty();
        }

        return getBoardSprints(boardId.get());
    }

    /**
     * Retrieves board sprints by board name.
     *
     * @param boardName The board name
     * @return Optional JSON response containing board sprints
     */
    public Optional<JsonNode> getBoardSprintsByName(String boardName) {
        Optional<Long> boardId = findBoardIdByName(boardName);
        if (boardId.isEmpty()) {
            logger.warn("Cannot retrieve board sprints - no board found for board name: {}", boardName);
            return Optional.empty();
        }

        return getBoardSprints(boardId.get());
    }

    /**
     * Retrieves board issues by project key with pagination.
     *
     * @param projectKey The project key
     * @param startAt Starting index for pagination
     * @param maxResults Maximum number of results per page
     * @return Optional JSON response containing board issues
     */
    public Optional<JsonNode> getBoardIssuesByProjectKey(String projectKey, int startAt, int maxResults) {
        Optional<Long> boardId = findBoardIdByProjectKey(projectKey);
        if (boardId.isEmpty()) {
            logger.warn("Cannot retrieve board issues - no board found for project key: {}", projectKey);
            return Optional.empty();
        }

        return getBoardIssues(boardId.get(), startAt, maxResults);
    }

    /**
     * Retrieves board issues by board name with pagination.
     *
     * @param boardName The board name
     * @param startAt Starting index for pagination
     * @param maxResults Maximum number of results per page
     * @return Optional JSON response containing board issues
     */
    public Optional<JsonNode> getBoardIssuesByName(String boardName, int startAt, int maxResults) {
        Optional<Long> boardId = findBoardIdByName(boardName);
        if (boardId.isEmpty()) {
            logger.warn("Cannot retrieve board issues - no board found for board name: {}", boardName);
            return Optional.empty();
        }

        return getBoardIssues(boardId.get(), startAt, maxResults);
    }

    /**
     * Retrieves board configuration by board ID.
     *
     * @param boardId The board ID
     * @return Optional JSON response containing board configuration
     */
    public Optional<JsonNode> getBoardConfiguration(Long boardId) {
        try {
            String endpoint = String.format("/rest/agile/1.0/board/%d/configuration", boardId);
            logger.debug("Fetching board configuration for board ID: {}", boardId);

            HttpEntity<String> requestEntity = new HttpEntity<>(defaultHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                jiraBaseUrl + endpoint,
                HttpMethod.GET,
                requestEntity,
                String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            logger.debug("Successfully retrieved board configuration for board ID: {}", boardId);
            return Optional.of(jsonNode);

        } catch (RestClientException e) {
            handleRestClientException(e, "fetching board configuration for board ID " + boardId);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error fetching board configuration for board ID: {}", boardId, e);
            return Optional.empty();
        }
    }

    /**
     * Retrieves board sprints by board ID.
     *
     * @param boardId The board ID
     * @return Optional JSON response containing board sprints
     */
    public Optional<JsonNode> getBoardSprints(Long boardId) {
        try {
            String endpoint = String.format("/rest/agile/1.0/board/%d/sprint", boardId);
            logger.debug("Fetching sprints for board ID: {}", boardId);

            HttpEntity<String> requestEntity = new HttpEntity<>(defaultHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                jiraBaseUrl + endpoint,
                HttpMethod.GET,
                requestEntity,
                String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            logger.debug("Successfully retrieved {} sprints for board ID: {}",
                        jsonNode.path("values").size(), boardId);
            return Optional.of(jsonNode);

        } catch (RestClientException e) {
            handleRestClientException(e, "fetching sprints for board ID " + boardId);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error fetching sprints for board ID: {}", boardId, e);
            return Optional.empty();
        }
    }

    /**
     * Retrieves board issues by board ID with pagination.
     *
     * @param boardId The board ID
     * @param startAt Starting index for pagination
     * @param maxResults Maximum number of results per page
     * @return Optional JSON response containing board issues
     */
    public Optional<JsonNode> getBoardIssues(Long boardId, int startAt, int maxResults) {
        try {
            String endpoint = String.format("/rest/agile/1.0/board/%d/issue?startAt=%d&maxResults=%d",
                                           boardId, startAt, maxResults);
            logger.debug("Fetching issues for board ID: {} (startAt: {}, maxResults: {})",
                        boardId, startAt, maxResults);

            HttpEntity<String> requestEntity = new HttpEntity<>(defaultHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                jiraBaseUrl + endpoint,
                HttpMethod.GET,
                requestEntity,
                String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            logger.debug("Successfully retrieved {} issues for board ID: {} (startAt: {})",
                        jsonNode.path("issues").size(), boardId, startAt);
            return Optional.of(jsonNode);

        } catch (RestClientException e) {
            handleRestClientException(e, "fetching issues for board ID " + boardId);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error fetching issues for board ID: {}", boardId, e);
            return Optional.empty();
        }
    }

    /**
     * Retrieves team information from JIRA.
     *
     * @param teamId The JIRA team ID
     * @return Optional JSON response containing team details
     */
    public Optional<JsonNode> getTeamDetails(String teamId) {
        try {
            String endpoint = String.format("/rest/teams/1.0/teams/%s", teamId);
            logger.debug("Fetching team details for team ID: {}", teamId);

            HttpEntity<String> requestEntity = new HttpEntity<>(defaultHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                jiraBaseUrl + endpoint,
                HttpMethod.GET,
                requestEntity,
                String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            logger.debug("Successfully retrieved team details for team ID: {}", teamId);
            return Optional.of(jsonNode);

        } catch (RestClientException e) {
            handleRestClientException(e, "fetching team details for team ID " + teamId);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error fetching team details for team ID: {}", teamId, e);
            return Optional.empty();
        }
    }

    /**
     * Retrieves all teams from JIRA.
     *
     * @return Optional JSON response containing all teams
     */
    public Optional<JsonNode> getAllTeams() {
        try {
            String endpoint = "/rest/teams/1.0/teams";
            logger.debug("Fetching all teams from JIRA");

            HttpEntity<String> requestEntity = new HttpEntity<>(defaultHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                jiraBaseUrl + endpoint,
                HttpMethod.GET,
                requestEntity,
                String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            logger.debug("Successfully retrieved {} teams from JIRA",
                        jsonNode.path("values").size());
            return Optional.of(jsonNode);

        } catch (RestClientException e) {
            handleRestClientException(e, "fetching teams");
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error fetching teams", e);
            return Optional.empty();
        }
    }

    /**
     * Creates basic authentication header.
     *
     * @param username JIRA username
     * @param apiToken JIRA API token
     * @return Base64 encoded auth header
     */
    private String createBasicAuthHeader(String username, String apiToken) {
        String credentials = username + ":" + apiToken;
        byte[] credentialsBytes = credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] base64CredentialsBytes = java.util.Base64.getEncoder().encode(credentialsBytes);
        return "Basic " + new String(base64CredentialsBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Clears the board ID cache. Useful for testing or when board configurations change.
     */
    public void clearCache() {
        boardIdCache.clear();
        projectKeyToBoardIdCache.clear();
        logger.info("Board ID cache cleared");
    }

    /**
     * Handles RestClientException to log detailed error information.
     *
     * @param e The RestClientException to handle
     * @param actionDescription A description of the action being performed (for logging)
     */
    private void handleRestClientException(RestClientException e, String actionDescription) {
        if (e instanceof HttpStatusCodeException) {
            HttpStatusCodeException httpEx = (HttpStatusCodeException) e;
            logger.error("HTTP error {} while {}: Status: {}, Response: {}",
                        actionDescription, httpEx.getStatusCode(),
                        httpEx.getResponseBodyAsString(), e);
        } else {
            logger.error("Error {} - {}", actionDescription, e.getMessage(), e);
        }
    }

    /**
     * Tests the JIRA connection and authentication.
     * This method attempts a simple API call to verify credentials.
     *
     * @return true if authentication is successful, false otherwise
     */
    public boolean testConnection() {
        try {
            String endpoint = "/rest/api/2/myself";
            logger.info("Testing JIRA connection and authentication...");

            HttpEntity<String> requestEntity = new HttpEntity<>(defaultHeaders);
            ResponseEntity<String> response = restTemplate.exchange(
                jiraBaseUrl + endpoint,
                HttpMethod.GET,
                requestEntity,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                String displayName = jsonNode.path("displayName").asText("Unknown");
                String emailAddress = jsonNode.path("emailAddress").asText("Unknown");
                logger.info("JIRA connection successful! Authenticated as: {} ({})", displayName, emailAddress);
                return true;
            } else {
                logger.error("JIRA connection failed with status: {}", response.getStatusCode());
                return false;
            }

        } catch (RestClientException e) {
            logger.error("JIRA connection test failed - authentication error", e);
            handleRestClientException(e, "testing JIRA connection");
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error during JIRA connection test", e);
            return false;
        }
    }
}

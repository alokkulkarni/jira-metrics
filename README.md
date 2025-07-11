# JIRA Metrics Application

A Spring Boot application that automatically retrieves JIRA data from various boards and stores it in PostgreSQL. The application runs scheduled jobs every 2 hours to collect board data, issues, sprints, and team information from JIRA REST APIs.

## 🚀 Features

- **Automated Data Retrieval**: Scheduled job runs every 2 hours to fetch JIRA data
- **Multi-Board Support**: Configurable board IDs stored in database
- **Team Management**: Retrieves and stores team information from JIRA
- **Raw Data Storage**: Stores complete JSON responses for future analysis
- **REST API**: Endpoints for managing boards and viewing collected data
- **PostgreSQL Integration**: Robust data persistence with proper indexing
- **Comprehensive Testing**: Unit tests, integration tests with Testcontainers
- **Production Ready**: Actuator endpoints, proper logging, and error handling

## 📋 Prerequisites

- Java 17 or higher
- PostgreSQL 12+ database
- JIRA instance with API access
- Maven 3.6+

## 🛠️ Setup and Installation

### 1. Database Setup

Create a PostgreSQL database and user:

```sql
CREATE DATABASE jira_metrics;
CREATE USER jira_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE jira_metrics TO jira_user;
```

### 2. Environment Configuration

Create environment variables or update `application.properties`:

```bash
export DB_USERNAME=jira_user
export DB_PASSWORD=your_password
export JIRA_BASE_URL=https://your-domain.atlassian.net
export JIRA_USERNAME=your-email@domain.com
export JIRA_API_TOKEN=your-api-token
```

### 3. JIRA API Token Setup

1. Go to [Atlassian Account Settings](https://id.atlassian.com/manage-profile/security/api-tokens)
2. Create a new API token
3. Use your email and the token for authentication

### 4. Application Configuration

Update `src/main/resources/application.properties`:

```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/jira_metrics
spring.datasource.username=${DB_USERNAME:jira_user}
spring.datasource.password=${DB_PASSWORD:password}

# JIRA Configuration
jira.base-url=${JIRA_BASE_URL:https://your-domain.atlassian.net}
jira.username=${JIRA_USERNAME:your-email@domain.com}
jira.api-token=${JIRA_API_TOKEN:your-api-token}
```

## 🏃‍♂️ Running the Application

### Using Maven

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Run the application
mvn spring-boot:run
```

### Using Docker (Optional)

```bash
# Build the application
mvn clean package -DskipTests

# Create Docker image
docker build -t jira-metrics .

# Run with Docker Compose
docker-compose up -d
```

## 📊 Database Schema

The application creates the following tables:

### Boards Table
- Stores JIRA board configurations
- Fields: id, board_id, board_name, project_key, is_active, created_at, updated_at

### Teams Table
- Stores team information from JIRA
- Fields: id, team_id, team_name, description, lead_account_id, lead_display_name, member_count, is_active, created_at, updated_at

### JIRA Data Table
- Stores raw JSON responses from JIRA APIs
- Fields: id, board_id, team_id, data_type, raw_data, retrieval_timestamp, record_count, created_at

## 🔧 API Endpoints

### Board Management

```http
# Get all active boards
GET /api/v1/boards

# Get specific board
GET /api/v1/boards/{boardId}

# Create new board
POST /api/v1/boards
Content-Type: application/json
{
  "boardId": 123,
  "boardName": "Development Board",
  "projectKey": "DEV"
}

# Update board
PUT /api/v1/boards/{boardId}

# Deactivate board
DELETE /api/v1/boards/{boardId}
```

### Data Retrieval

```http
# Get all teams
GET /api/v1/teams

# Get board data
GET /api/v1/boards/{boardId}/data

# Get latest board data by type
GET /api/v1/boards/{boardId}/data/latest?dataType=issues

# Manually trigger data refresh
POST /api/v1/data/refresh

# Health check
GET /api/v1/health
```

### Actuator Endpoints

```http
# Application health
GET /actuator/health

# Scheduled tasks info
GET /actuator/scheduledtasks

# Application info
GET /actuator/info
```

## ⏰ Scheduled Jobs

### Main Data Retrieval Job
- **Schedule**: Every 2 hours (`0 0 */2 * * *`)
- **Function**: Retrieves data from all active boards
- **Data Types**: Board configuration, issues, sprints, team information

### Health Check Job
- **Schedule**: Daily at midnight (`0 0 0 * * *`)
- **Function**: Logs scheduler health status

## 🧪 Testing

The application includes comprehensive tests following best practices:

### Running Tests

```bash
# Run all tests
mvn test

# Run only unit tests
mvn test -Dtest="unit/**/*Test"

# Run only integration tests
mvn test -Dtest="integration/**/*Test"

# Run with coverage
mvn test jacoco:report
```

### Test Structure

```
src/test/java/
├── unit/                    # Unit tests with mocks
├── integration/             # Integration tests with Testcontainers
├── bdd/                     # BDD tests (future)
└── regression/              # Regression tests (future)
```

## 📈 Monitoring and Observability

### Logging

The application uses SLF4J with structured logging:

- **DEBUG**: Detailed API interactions
- **INFO**: Job execution and major operations
- **WARN**: Non-critical issues
- **ERROR**: Failures and exceptions

### Metrics

Available through Spring Boot Actuator:

- HTTP request metrics
- Database connection pool metrics
- JVM metrics
- Custom business metrics

## 🔒 Security Considerations

- API tokens stored as environment variables
- Database credentials externalized
- No sensitive data logged
- Input validation on all endpoints
- Proper error handling without data exposure

## 🚀 Production Deployment

### Environment Variables

```bash
# Database
DB_USERNAME=production_user
DB_PASSWORD=secure_password

# JIRA API
JIRA_BASE_URL=https://company.atlassian.net
JIRA_USERNAME=service-account@company.com
JIRA_API_TOKEN=production_token

# Application
SPRING_PROFILES_ACTIVE=production
```

### Performance Tuning

```properties
# Database connection pool
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5

# Task scheduler
spring.task.scheduling.pool.size=5

# HTTP client timeouts
jira.client.timeout.seconds=30
```

## 📚 Data Analysis

The raw JIRA data is stored as JSON, enabling:

- Custom analytics and reporting
- Historical trend analysis
- Team performance metrics
- Sprint velocity calculations
- Issue tracking and resolution times

Example queries:

```sql
-- Get latest issues for a board
SELECT raw_data 
FROM jira_data 
WHERE board_id = 123 
  AND data_type = 'issues' 
ORDER BY retrieval_timestamp DESC 
LIMIT 1;

-- Count data retrievals by board
SELECT board_id, COUNT(*) as retrieval_count
FROM jira_data 
WHERE retrieval_timestamp > NOW() - INTERVAL '7 days'
GROUP BY board_id;
```

## 🛠️ Troubleshooting

### Common Issues

1. **Database Connection Issues**
   - Verify PostgreSQL is running
   - Check connection credentials
   - Ensure database exists

2. **JIRA API Authentication**
   - Verify API token is valid
   - Check JIRA base URL format
   - Ensure user has board access

3. **Scheduled Job Not Running**
   - Check application logs
   - Verify `@EnableScheduling` annotation
   - Check system time and timezone

### Debug Mode

Enable debug logging:

```properties
logging.level.com.example.metrics.jira_metrics=DEBUG
logging.level.org.springframework.web.reactive.function.client=DEBUG
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Write tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 📞 Support

For issues and questions:

1. Check the troubleshooting section
2. Review application logs
3. Create an issue in the repository
4. Contact the development team

---

**Built with Spring Boot 3.5.3, Java 17, and PostgreSQL**

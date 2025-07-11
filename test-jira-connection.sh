#!/bin/bash

# JIRA API Token Setup and Test Script
# This script helps you set up your JIRA API token and test the connection

echo "🔧 JIRA Metrics - API Token Setup & Test"
echo "========================================"

# Check if JIRA_API_TOKEN is already set
if [ -z "$JIRA_API_TOKEN" ]; then
    echo "❌ JIRA_API_TOKEN environment variable is not set"
    echo ""
    echo "📋 To fix this:"
    echo "1. Go to: https://id.atlassian.com/manage-profile/security/api-tokens"
    echo "2. Create a new API token"
    echo "3. Export it as an environment variable:"
    echo ""
    echo "   export JIRA_API_TOKEN='your_token_here'"
    echo ""
    echo "4. Then run this script again"
    exit 1
else
    echo "✅ JIRA_API_TOKEN is set (length: ${#JIRA_API_TOKEN} characters)"
fi

# Start the application in the background
echo ""
echo "🚀 Starting JIRA Metrics application..."
mvn spring-boot:run &
APP_PID=$!

# Wait for application to start
echo "⏳ Waiting for application to start..."
sleep 15

# Test the connection
echo ""
echo "🔍 Testing JIRA connection..."
response=$(curl -s -w "\n%{http_code}" http://localhost:8080/api/v1/test-connection)
http_code=$(echo "$response" | tail -n1)
response_body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    echo "✅ JIRA connection successful!"
    echo "Response: $response_body"
else
    echo "❌ JIRA connection failed (HTTP $http_code)"
    echo "Response: $response_body"
    echo ""
    echo "🔧 Troubleshooting:"
    echo "- Check your API token is correct"
    echo "- Verify your JIRA URL: https://fintechclub.atlassian.net"
    echo "- Ensure your email: kulkarni.alok@gmail.com has access"
fi

# Stop the application
echo ""
echo "🛑 Stopping application..."
kill $APP_PID

echo "✅ Test complete!"

#!/bin/bash

# MCP Test Client - Startup Script
# This script builds and starts the MCP Test Client application

set -e

echo "========================================="
echo "  MCP Test Client - Startup"
echo "========================================="
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed or not in PATH"
    echo "   Please install Java 21 or higher"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
echo "✓ Java version: $(java -version 2>&1 | head -n 1)"
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "❌ Error: Java 21 or higher is required (found: $JAVA_VERSION)"
    exit 1
fi
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven is not installed or not in PATH"
    echo "   Please install Maven 3.8+"
    exit 1
fi

echo "✓ Maven version: $(mvn -version 2>&1 | head -n 1)"
echo ""

# Check if MCP server URL is configured
CONFIG_FILE="src/main/resources/application.properties"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "⚠️  Warning: Configuration file not found"
    echo "   Creating default configuration..."
    mkdir -p src/main/resources
    cat > "$CONFIG_FILE" << EOF
# Quarkus configuration
quarkus.http.port=8080

# MCP Server configuration
mcp.server.url=http://localhost:3001
mcp.server.name=Default MCP Server
EOF
    echo "✓ Default configuration created"
    echo ""
fi

# Ask user for MCP server URL if not configured or want to change
CURRENT_URL=$(grep "mcp.server.url" "$CONFIG_FILE" 2>/dev/null | cut -d'=' -f2 | tr -d '[:space:]')
if [ -n "$CURRENT_URL" ]; then
    echo "Current MCP Server URL: $CURRENT_URL"
    read -p "Change MCP server URL? (y/N): " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        read -p "Enter MCP Server URL: " NEW_URL
        if [ -n "$NEW_URL" ]; then
            sed -i "s|mcp.server.url=.*|mcp.server.url=$NEW_URL|" "$CONFIG_FILE"
            echo "✓ Updated MCP server URL to: $NEW_URL"
            echo ""
        fi
    fi
fi

# Build the application
echo "🔨 Building application..."
mvn clean package -DskipTests -q

if [ $? -eq 0 ]; then
    echo "✓ Build successful"
    echo ""
else
    echo "❌ Build failed"
    exit 1
fi

# Start the application
echo "🚀 Starting MCP Test Client..."
echo ""
echo "========================================="
echo "  Application will be available at:"
echo "  http://localhost:8080"
echo "========================================="
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

# Run the application
java -jar target/quarkus-app/quarkus-run.jar

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
echo "  http://localhost:8085"
echo "========================================="
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

# Run the application
java -jar target/quarkus-app/quarkus-run.jar

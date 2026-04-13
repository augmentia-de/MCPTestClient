#!/bin/bash

# MCP Test Client - Development Mode Startup Script
# This script starts the application in Quarkus development mode with hot reload

set -e

echo "========================================="
echo "  MCP Test Client - Development Mode"
echo "========================================="
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed or not in PATH"
    echo "   Please install Java 21 or higher"
    exit 1
fi

echo "✓ Java version: $(java -version 2>&1 | head -n 1)"
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven is not installed or not in PATH"
    echo "   Please install Maven 3.8+"
    exit 1
fi

echo "✓ Maven version: $(mvn -version 2>&1 | head -n 1)"
echo ""

echo "🚀 Starting Quarkus in development mode..."
echo ""
echo "========================================="
echo "  Application will be available at:"
echo "  http://localhost:8080"
echo ""
echo "  Features:"
echo "  - Hot reload on code changes"
echo "  - Live coding support"
echo "  - Dev UI at http://localhost:8080/q/dev"
echo "========================================="
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

# Start in development mode
mvn quarkus:dev

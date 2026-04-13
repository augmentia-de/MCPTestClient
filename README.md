# MCP Test Client

A Quarkus-based web application that connects to an MCP (Model Context Protocol) server, discovers its tools, and provides an interactive HTML frontend for testing tool calls.

## Features

- **MCP Server Connection**: Connects to MCP servers via Streamable HTTP transport (LangChain4j)
- **Manual Connect/Disconnect**: UI controls to manage the server connection
- **Tool Discovery**: Automatically discovers all available tools from the MCP server
- **Interactive Frontend**: Modern, responsive web UI to browse and test tools
- **JSON Editor**: Edit tool parameters as JSON before execution
- **Schema Display**: Shows parameter types, requirements, and descriptions for each tool
- **Real-time Testing**: Call tools directly from the web interface and view results

## Technology Stack

- **Backend**: Quarkus 3.33 (LTS)
- **MCP Client**: LangChain4j MCP Client 1.12.2-beta22 (`StreamableHttpMcpTransport` + `DefaultMcpClient`)
- **Frontend**: Pure HTML/CSS/JavaScript (no framework dependencies)
- **Build Tool**: Maven

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- A running MCP server (e.g., Quarkus MCP server, filesystem server, custom MCP server)

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Quarkus HTTP port
quarkus.http.port=8082

# MCP Server configuration
# Use the BASE URL (StreamableHttpMcpTransport handles the endpoints internally)
mcp.server.url=http://localhost:8480/mcp
```

> **Important**: Use the base URL without `/sse` or `/messages` suffix. The LangChain4j `StreamableHttpMcpTransport` handles the endpoint resolution automatically.

## Building

```bash
mvn clean package -DskipTests
```

## Running

### Development Mode

```bash
mvn quarkus:dev
```

The application will be available at: http://localhost:8082

### Production Mode

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

### Using the Startup Scripts

```bash
./dev.sh      # Development mode with hot reload
./start.sh    # Production mode (packaged JAR)
```

## Usage

1. **Start your MCP server** - Ensure your MCP server is running and accessible
2. **Start the MCP Test Client** - Run in dev or production mode
3. **Open the web interface** - Navigate to http://localhost:8082
4. **Connect to server** - Click the **🔌 Connect** button in the sidebar
5. **Browse tools** - The left sidebar shows all available tools
6. **Select a tool** - Click on a tool to view its details and parameter schema
7. **Edit parameters** - Modify the JSON in the editor as needed
8. **Call the tool** - Click **🚀 Call Tool** to execute and view results
9. **Disconnect** - Click **⛔ Disconnect** to close the connection

## REST API

The application provides the following REST endpoints:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/mcp/server` | Get MCP server information and connection status |
| POST | `/api/mcp/connect` | Connect to the MCP server |
| POST | `/api/mcp/disconnect` | Disconnect from the MCP server |
| GET | `/api/mcp/tools` | List all available tools |
| GET | `/api/mcp/tools/{toolName}` | Get details for a specific tool |
| POST | `/api/mcp/tools/{toolName}/call` | Call a tool with arguments |
| POST | `/api/mcp/refresh` | Refresh tool list from server |

### Example: Connect and Call a Tool

```bash
# Connect to server
curl -X POST http://localhost:8082/api/mcp/connect

# List tools
curl http://localhost:8082/api/mcp/tools

# Call a tool
curl -X POST http://localhost:8082/api/mcp/tools/my-tool/call \
  -H "Content-Type: application/json" \
  -d '{"arguments": {"param1": "value1", "param2": 42}}'
```

## Project Structure

```
MCPTestClient/
├── src/main/java/com/example/mcp/
│   ├── dto/
│   │   ├── ToolDTO.java              # Tool data transfer object
│   │   ├── ToolCallRequestDTO.java   # Tool call request DTO
│   │   └── ToolCallResponseDTO.java  # Tool call response DTO
│   ├── resource/
│   │   └── McpResource.java          # REST API endpoints
│   └── service/
│       ├── McpClientService.java     # MCP client (LangChain4j-based)
│       └── McpManager.java           # MCP manager (startup connection test)
├── src/main/resources/
│   ├── META-INF/resources/
│   │   └── index.html                # Web frontend
│   └── application.properties        # Configuration
└── pom.xml                           # Maven build file
```

## Example MCP Servers

### Quarkus MCP Server (SSE)

```java
// quarkus-mcp-server-sse dependency
// Runs at http://localhost:8480/mcp
```

### Filesystem Server

```bash
npx -y @modelcontextprotocol/server-filesystem /tmp
```

### Custom Server

You can implement your own MCP server using:
- [Quarkus MCP Server](https://github.com/quarkiverse/quarkus-mcp-server)
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Browser (HTML/JS)                       │
│  ┌───────────┐  ┌──────────────┐  ┌────────────────────┐   │
│  │ Server    │  │ Tool List    │  │ JSON Editor +      │   │
│  │ Info      │  │ (sidebar)    │  │ Call/Result Panel  │   │
│  └───────────┘  └──────────────┘  └────────────────────┘   │
└─────────────────────────┬───────────────────────────────────┘
                          │ REST API (JSON)
┌─────────────────────────┴───────────────────────────────────┐
│                      Quarkus Backend                         │
│  ┌──────────────┐  ┌──────────────────────────────────────┐ │
│  │ McpResource  │──│ McpClientService                     │ │
│  │ (REST API)   │  │ • StreamableHttpMcpTransport         │ │
│  └──────────────┘  │ • DefaultMcpClient (LangChain4j)     │ │
│                    │ • Tool cache + schema extraction      │ │
│                    └──────────────────────────────────────┘ │
└─────────────────────────┬───────────────────────────────────┘
                          │ Streamable HTTP
┌─────────────────────────┴───────────────────────────────────┐
│                    MCP Server                                │
│              (Quarkus / Node.js / Python)                    │
└─────────────────────────────────────────────────────────────┘
```

## Troubleshooting

### Connection Failed

- Verify the MCP server is running
- Use the **base URL** without `/sse` suffix (e.g., `http://localhost:8480/mcp`)
- Check server logs for errors
- Ensure the server supports Streamable HTTP transport

### No Tools Listed

- Make sure you clicked **Connect** first
- The MCP server may not expose any tools
- Check server logs for errors

### Tool Call Fails

- Verify the JSON arguments match the tool's schema
- Check the result panel for error messages from the server
- Ensure all required parameters are provided

### Port Conflict

- The app runs on port 8082 by default
- Change `quarkus.http.port` in `application.properties` if needed

## License

This project is provided as-is for testing and learning purposes.

package com.example.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP Client Service - connects to MCP server, discovers tools, and executes them.
 * Uses LangChain4J StreamableHttpMcpTransport (same as McpManager).
 */
@ApplicationScoped
public class McpClientService {

    private static final Logger LOG = Logger.getLogger(McpClientService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @ConfigProperty(name = "mcp.server.url", defaultValue = "http://localhost:8480/mcp")
    String serverUrl;

    @ConfigProperty(name = "mcp.server.name", defaultValue = "MCP Server")
    String serverName;

    private McpClient mcpClient;
    private McpTransport transport;
    private String serverVersion = "unknown";
    private final Map<String, ToolSpecification> toolCache = new ConcurrentHashMap<>();
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private String connectionError = null;

    /**
     * Connect to the MCP server.
     */
    public synchronized void connect() {
        if (connecting.get()) {
            LOG.info("Connection already in progress");
            return;
        }

        if (isConnected()) {
            LOG.info("Already connected to MCP server");
            return;
        }

        connecting.set(true);
        connectionError = null;

        try {
            LOG.infof("Connecting to MCP server at: %s", serverUrl);

            // Same transport as McpManager
            transport = new StreamableHttpMcpTransport.Builder()
                    .url(serverUrl)
                    .logRequests(true)
                    .logResponses(true)
                    .timeout(Duration.ofMinutes(5))
                    .build();

            // Same client as McpManager
            mcpClient = new DefaultMcpClient.Builder()
                    .transport(transport)
                    .build();

            // Load tools
            refreshTools();

            serverVersion = "connected";
            connectionError = null;
            LOG.infof("Connected to MCP server, loaded %d tools", toolCache.size());

        } catch (Exception e) {
            connectionError = e.getMessage();
            LOG.errorf("Failed to connect to MCP server: %s", e.getMessage());
            mcpClient = null;
            transport = null;
            serverVersion = "unknown";
            toolCache.clear();
        } finally {
            connecting.set(false);
        }
    }

    /**
     * Disconnect from the MCP server.
     */
    public synchronized void disconnect() {
        if (mcpClient != null) {
            try {
                mcpClient.close();
                LOG.info("Disconnected from MCP server");
            } catch (Exception e) {
                LOG.errorf("Error closing MCP client: %s", e.getMessage());
            }
            mcpClient = null;
            transport = null;
            serverVersion = "unknown";
            toolCache.clear();
        }
    }

    @PreDestroy
    public void cleanup() {
        disconnect();
    }

    /**
     * Refresh the list of available tools from the server.
     */
    public void refreshTools() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MCP server");
        }

        toolCache.clear();
        try {
            List<ToolSpecification> tools = mcpClient.listTools();
            if (tools != null) {
                for (ToolSpecification tool : tools) {
                    toolCache.put(tool.name(), tool);
                }
                LOG.infof("Loaded %d tools from server", toolCache.size());
            }
        } catch (Exception e) {
            LOG.errorf("Error loading tools: %s", e.getMessage());
            throw new RuntimeException("Failed to load tools", e);
        }
    }

    /**
     * Get server version info.
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * Get the configured server URL.
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Get the server name.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Get all available tools.
     */
    public List<ToolSpecification> getAllTools() {
        return new ArrayList<>(toolCache.values());
    }

    /**
     * Get a specific tool by name.
     */
    public ToolSpecification getTool(String toolName) {
        return toolCache.get(toolName);
    }

    /**
     * Call a tool with the given arguments.
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MCP server. Please connect first.");
        }

        ToolSpecification tool = toolCache.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        LOG.infof("Calling tool: %s with arguments: %s", toolName, arguments);

        // Serialize arguments to JSON
        String argumentsJson;
        try {
            argumentsJson = JSON.writeValueAsString(arguments);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize arguments: " + e.getMessage(), e);
        }

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(UUID.randomUUID().toString())
                .name(toolName)
                .arguments(argumentsJson)
                .build();

        ToolExecutionResult result = mcpClient.executeTool(request);
        
        // Try different method names based on version
        // In newer versions it's resultText(), in older it might be content() or text()
        try {
            return result.resultText();
        } catch (NoSuchMethodError e) {
            try {
                return (String) result.getClass().getMethod("content").invoke(result);
            } catch (Exception ex) {
                return result.toString();
            }
        }
    }

    /**
     * Convert ToolSpecification to a Map for JSON serialization.
     */
    public Map<String, Object> toolToMap(ToolSpecification tool) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", tool.name());
        map.put("description", tool.description() != null ? tool.description() : "");

        if (tool.parameters() != null) {
            try {
                dev.langchain4j.model.chat.request.json.JsonObjectSchema params = tool.parameters();
                
                Map<String, Object> schema = new HashMap<>();
                schema.put("type", "object");
                
                // Extract properties - params.properties() returns Map<String, JsonSchemaElement>
                Map<String, Object> properties = new HashMap<>();
                if (params.properties() != null) {
                    for (var entry : params.properties().entrySet()) {
                        properties.put(entry.getKey(), schemaElementToJson(entry.getValue()));
                    }
                }
                schema.put("properties", properties);
                
                if (params.required() != null && !params.required().isEmpty()) {
                    schema.put("required", new ArrayList<>(params.required()));
                }
                
                map.put("inputSchema", schema);
            } catch (Exception e) {
                LOG.debugf("Could not serialize parameters for tool %s: %s", tool.name(), e.getMessage());
                // Fallback: return basic schema
                Map<String, Object> basic = new HashMap<>();
                basic.put("type", "object");
                basic.put("properties", Map.of());
                map.put("inputSchema", basic);
            }
        } else {
            Map<String, Object> empty = new HashMap<>();
            empty.put("type", "object");
            empty.put("properties", Map.of());
            map.put("inputSchema", empty);
        }

        return map;
    }

    /**
     * Convert JsonSchemaElement to a Map for JSON serialization.
     */
    private Map<String, Object> schemaElementToJson(dev.langchain4j.model.chat.request.json.JsonSchemaElement element) {
        Map<String, Object> schemaMap = new HashMap<>();
        
        if (element instanceof dev.langchain4j.model.chat.request.json.JsonStringSchema s) {
            schemaMap.put("type", "string");
            if (s.description() != null) schemaMap.put("description", s.description());
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonIntegerSchema s) {
            schemaMap.put("type", "integer");
            if (s.description() != null) schemaMap.put("description", s.description());
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonNumberSchema s) {
            schemaMap.put("type", "number");
            if (s.description() != null) schemaMap.put("description", s.description());
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonBooleanSchema s) {
            schemaMap.put("type", "boolean");
            if (s.description() != null) schemaMap.put("description", s.description());
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonArraySchema s) {
            schemaMap.put("type", "array");
            if (s.description() != null) schemaMap.put("description", s.description());
            if (s.items() != null) schemaMap.put("items", schemaElementToJson(s.items()));
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonObjectSchema s) {
            schemaMap.put("type", "object");
            if (s.description() != null) schemaMap.put("description", s.description());
            if (s.properties() != null) {
                Map<String, Object> props = new HashMap<>();
                for (var entry : s.properties().entrySet()) {
                    props.put(entry.getKey(), schemaElementToJson(entry.getValue()));
                }
                schemaMap.put("properties", props);
            }
            if (s.required() != null && !s.required().isEmpty()) {
                schemaMap.put("required", new ArrayList<>(s.required()));
            }
        } else {
            // Fallback - just mark as unknown
            schemaMap.put("type", "unknown");
        }
        
        return schemaMap;
    }

    /**
     * Check if the client is connected.
     */
    public boolean isConnected() {
        return mcpClient != null;
    }

    /**
     * Check if a connection attempt is in progress.
     */
    public boolean isConnecting() {
        return connecting.get();
    }

    /**
     * Get the last connection error message.
     */
    public String getConnectionError() {
        return connectionError;
    }
}

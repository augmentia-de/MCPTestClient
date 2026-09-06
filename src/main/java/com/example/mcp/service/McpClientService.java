package com.example.mcp.service;

import com.example.mcp.config.McpServerConfig;
import com.example.mcp.config.McpServersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class McpClientService {

    private static final Logger LOG = Logger.getLogger(McpClientService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DEFAULT_CONFIG_FILE = "mcp-servers.json";

    @ConfigProperty(name = "mcp.config.path", defaultValue = "./config")
    String configPath;

    private List<McpServerConfig> configuredServers = List.of();
    private McpClient mcpClient;
    private McpTransport transport;
    private String serverVersion = "unknown";
    private final Map<String, ToolSpecification> toolCache = new ConcurrentHashMap<>();
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private String connectionError = null;
    private String activeServerName = null;
    private String activeServerUrl = null;

    @PostConstruct
    void init() {
        loadServerConfig();
    }

    private void loadServerConfig() {
        // Try external config path first (e.g. ./config/mcp-servers.json)
        java.nio.file.Path externalPath = java.nio.file.Paths.get(configPath, DEFAULT_CONFIG_FILE);
        try {
            if (java.nio.file.Files.exists(externalPath)) {
                LOG.infof("Loading MCP server config from: %s", externalPath.toAbsolutePath());
                McpServersConfig config = JSON.readValue(externalPath.toFile(), McpServersConfig.class);
                if (config.getServers() != null && !config.getServers().isEmpty()) {
                    configuredServers = config.getServers();
                    LOG.infof("Loaded %d MCP server(s) from %s", configuredServers.size(), externalPath.toAbsolutePath());
                    return;
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to load config from %s: %s", externalPath.toAbsolutePath(), e.getMessage());
        }

        // Fallback: classpath resource
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG_FILE)) {
            if (is == null) {
                LOG.warnf("Config file '%s' not found on classpath", DEFAULT_CONFIG_FILE);
                configuredServers = List.of();
                return;
            }
            McpServersConfig config = JSON.readValue(is, McpServersConfig.class);
            if (config.getServers() == null || config.getServers().isEmpty()) {
                LOG.warn("No servers defined in mcp-servers.json");
                configuredServers = List.of();
                return;
            }
            configuredServers = config.getServers();
            LOG.infof("Loaded %d MCP server(s) from classpath:%s", configuredServers.size(), DEFAULT_CONFIG_FILE);
        } catch (MismatchedInputException e) {
            LOG.warnf("Config file '%s' is empty or malformed: %s", DEFAULT_CONFIG_FILE, e.getMessage());
            configuredServers = List.of();
        } catch (Exception e) {
            LOG.errorf("Failed to load config file '%s': %s", DEFAULT_CONFIG_FILE, e.getMessage());
            configuredServers = List.of();
        }
    }

    public List<McpServerConfig> getConfiguredServers() {
        return Collections.unmodifiableList(configuredServers);
    }

    public synchronized void connect() {
        if (configuredServers.isEmpty()) {
            connectionError = "No MCP servers configured";
            return;
        }
        connect(configuredServers.get(0).getName());
    }

    public synchronized void connect(String serverName) {
        if (connecting.get()) {
            LOG.info("Connection already in progress");
            return;
        }

        if (isConnected() && serverName.equals(activeServerName)) {
            LOG.infof("Already connected to '%s'", serverName);
            return;
        }

        if (isConnected()) {
            disconnect();
        }

        McpServerConfig server = configuredServers.stream()
                .filter(s -> s.getName().equals(serverName))
                .findFirst()
                .orElse(null);

        if (server == null) {
            connectionError = "Server not found: " + serverName;
            return;
        }

        connecting.set(true);
        connectionError = null;

        try {
            LOG.infof("Connecting to MCP server '%s' at: %s", server.getName(), server.getUrl());

            String transportType = server.getTransport() != null ? server.getTransport().toUpperCase() : "STREAMABLE_HTTP";

            if ("SSE".equals(transportType)) {
                HttpMcpTransport.Builder transportBuilder = new HttpMcpTransport.Builder()
                        .sseUrl(server.getUrl())
                        .logRequests(true)
                        .logResponses(true)
                        .timeout(Duration.ofMinutes(5));

                String token = server.getBearerToken();
                if (token != null && !token.isEmpty()) {
                    LOG.info("Using Bearer token authentication for SSE transport");
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + token);
                    transportBuilder.customHeaders(headers);
                }

                transport = transportBuilder.build();
            } else {
                StreamableHttpMcpTransport.Builder transportBuilder = new StreamableHttpMcpTransport.Builder()
                        .url(server.getUrl())
                        .logRequests(true)
                        .logResponses(true)
                        .timeout(Duration.ofMinutes(5));

                String token = server.getBearerToken();
                if (token != null && !token.isEmpty()) {
                    LOG.info("Using Bearer token authentication");
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + token);
                    transportBuilder.customHeaders(headers);
                }

                transport = transportBuilder.build();
            }

            mcpClient = new DefaultMcpClient.Builder()
                    .transport(transport)
                    .build();

            refreshTools();

            activeServerName = server.getName();
            activeServerUrl = server.getUrl();
            serverVersion = "connected";
            connectionError = null;
            LOG.infof("Connected to '%s', loaded %d tools", server.getName(), toolCache.size());

        } catch (Exception e) {
            connectionError = e.getMessage();
            LOG.errorf("Failed to connect to '%s': %s", serverName, e.getMessage());
            mcpClient = null;
            transport = null;
            serverVersion = "unknown";
            toolCache.clear();
            activeServerName = null;
            activeServerUrl = null;
        } finally {
            connecting.set(false);
        }
    }

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
            activeServerName = null;
            activeServerUrl = null;
        }
    }

    @PreDestroy
    public void cleanup() {
        disconnect();
    }

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

    public String getServerVersion() {
        return serverVersion;
    }

    public String getActiveServerName() {
        return activeServerName;
    }

    public String getActiveServerUrl() {
        return activeServerUrl;
    }

    public List<ToolSpecification> getAllTools() {
        return new ArrayList<>(toolCache.values());
    }

    public ToolSpecification getTool(String toolName) {
        return toolCache.get(toolName);
    }

    public String callTool(String toolName, Map<String, Object> arguments) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to MCP server. Please connect first.");
        }

        ToolSpecification tool = toolCache.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        LOG.infof("Calling tool: %s with arguments: %s", toolName, arguments);

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

    public Map<String, Object> toolToMap(ToolSpecification tool) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", tool.name());
        map.put("description", tool.description() != null ? tool.description() : "");

        if (tool.parameters() != null) {
            try {
                dev.langchain4j.model.chat.request.json.JsonObjectSchema params = tool.parameters();

                Map<String, Object> schema = new HashMap<>();
                schema.put("type", "object");

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
            schemaMap.put("type", "unknown");
        }

        return schemaMap;
    }

    public McpClient getMcpClient() {
        return mcpClient;
    }

    public boolean isConnected() {
        return mcpClient != null;
    }

    public boolean isConnecting() {
        return connecting.get();
    }

    public String getConnectionError() {
        return connectionError;
    }
}
package com.example.mcp.resource;

import com.example.mcp.config.McpServerConfig;
import com.example.mcp.dto.ToolCallRequestDTO;
import com.example.mcp.dto.ToolCallResponseDTO;
import com.example.mcp.dto.ToolDTO;
import com.example.mcp.service.McpClientService;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/mcp")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class McpResource {

    private static final Logger LOG = Logger.getLogger(McpResource.class);

    @Inject
    McpClientService mcpClientService;

    @GET
    @Path("/servers")
    public Response getServers() {
        try {
            List<McpServerConfig> servers = mcpClientService.getConfiguredServers();
            List<Map<String, Object>> result = servers.stream().map(s -> {
                Map<String, Object> m = new HashMap<>();
                m.put("name", s.getName());
                m.put("url", s.getUrl());
                m.put("hasAuth", s.getBearerToken() != null && !s.getBearerToken().isEmpty());
                return m;
            }).collect(Collectors.toList());
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.errorf("Error getting servers: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get servers: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/server")
    public Response getServerInfo() {
        try {
            Map<String, Object> serverInfo = new HashMap<>();
            String activeUrl = mcpClientService.getActiveServerUrl();
            String activeName = mcpClientService.getActiveServerName();
            McpServerConfig first = mcpClientService.getConfiguredServers().isEmpty()
                    ? null
                    : mcpClientService.getConfiguredServers().get(0);
            serverInfo.put("url", activeUrl != null ? activeUrl : (first != null ? first.getUrl() : null));
            serverInfo.put("configuredName", activeName != null ? activeName : (first != null ? first.getName() : null));
            serverInfo.put("activeServer", activeName);
            serverInfo.put("connecting", mcpClientService.isConnecting());
            serverInfo.put("connected", mcpClientService.isConnected());
            serverInfo.put("name", mcpClientService.isConnected() ? mcpClientService.getActiveServerName() : null);
            serverInfo.put("version", mcpClientService.getServerVersion());
            serverInfo.put("error", mcpClientService.getConnectionError());
            return Response.ok(serverInfo).build();
        } catch (Exception e) {
            LOG.errorf("Error getting server info: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get server info: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/connect")
    public Response connect(@QueryParam("server") String serverName) {
        try {
            if (mcpClientService.isConnected()) {
                return Response.ok(new SuccessResponse("Already connected")).build();
            }

            String target = (serverName != null && !serverName.isEmpty()) ? serverName : null;
            new Thread(() -> {
                if (target != null) {
                    mcpClientService.connect(target);
                } else {
                    mcpClientService.connect();
                }
            }).start();

            return Response.accepted(new SuccessResponse("Connecting to server...")).build();
        } catch (Exception e) {
            LOG.errorf("Error connecting: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to connect: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/disconnect")
    public Response disconnect() {
        try {
            mcpClientService.disconnect();
            return Response.ok(new SuccessResponse("Disconnected")).build();
        } catch (Exception e) {
            LOG.errorf("Error disconnecting: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to disconnect: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/tools")
    public Response getTools() {
        try {
            if (!mcpClientService.isConnected()) {
                return Response.ok(List.of()).build();
            }

            List<ToolDTO> tools = mcpClientService.getAllTools().stream()
                    .map(mcpClientService::toolToMap)
                    .map(this::mapToToolDTO)
                    .collect(Collectors.toList());
            return Response.ok(tools).build();
        } catch (Exception e) {
            LOG.errorf("Error getting tools: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get tools: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/tools/{toolName}")
    public Response getTool(@PathParam("toolName") String toolName) {
        try {
            if (!mcpClientService.isConnected()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(new ErrorResponse("Not connected to MCP server. Please connect first."))
                        .build();
            }

            ToolSpecification tool = mcpClientService.getTool(toolName);
            if (tool == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Tool not found: " + toolName))
                        .build();
            }

            return Response.ok(mapToToolDTO(mcpClientService.toolToMap(tool))).build();
        } catch (Exception e) {
            LOG.errorf("Error getting tool %s: %s", toolName, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get tool: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/tools/{toolName}/call")
    public Response callTool(@PathParam("toolName") String toolName, ToolCallRequestDTO request) {
        try {
            if (!mcpClientService.isConnected()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(new ErrorResponse("Not connected to MCP server. Please connect first."))
                        .build();
            }

            if (request == null || request.getArguments() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Request body with arguments is required"))
                        .build();
            }

            String result = mcpClientService.callTool(toolName, request.getArguments());

            ToolCallResponseDTO responseDTO = new ToolCallResponseDTO(false,
                    List.of(new ToolCallResponseDTO.ContentDTO("text", result)));
            return Response.ok(responseDTO).build();
        } catch (IllegalArgumentException e) {
            LOG.errorf("Tool not found: %s", toolName);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (IllegalStateException e) {
            LOG.errorf("Not connected: %s", e.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.errorf("Error calling tool %s: %s", toolName, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to call tool: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/refresh")
    public Response refreshTools() {
        try {
            if (!mcpClientService.isConnected()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(new ErrorResponse("Not connected to MCP server. Please connect first."))
                        .build();
            }

            mcpClientService.refreshTools();
            return Response.ok(new SuccessResponse("Tools refreshed successfully")).build();
        } catch (Exception e) {
            LOG.errorf("Error refreshing tools: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to refresh tools: " + e.getMessage()))
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private ToolDTO mapToToolDTO(Map<String, Object> toolMap) {
        String name = (String) toolMap.getOrDefault("name", "unknown");
        String description = (String) toolMap.getOrDefault("description", "");
        Map<String, Object> inputSchema = (Map<String, Object>) toolMap.get("inputSchema");

        if (inputSchema == null) {
            inputSchema = Map.of();
        }

        return new ToolDTO(name, description, inputSchema);
    }

    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public static class SuccessResponse {
        private String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
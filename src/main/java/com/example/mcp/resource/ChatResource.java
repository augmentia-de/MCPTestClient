package com.example.mcp.resource;

import com.example.mcp.dto.ChatHistoryEntryDTO;
import com.example.mcp.dto.ChatMessageRequestDTO;
import com.example.mcp.dto.ChatResponseDTO;
import com.example.mcp.service.AiChatService;
import com.example.mcp.service.McpClientService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {

    private static final Logger LOG = Logger.getLogger(ChatResource.class);

    @Inject
    AiChatService aiChatService;

    @Inject
    McpClientService mcpClientService;

    @GET
    @Path("/status")
    public Response status() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", aiChatService.isEnabled());
        status.put("connected", mcpClientService.isConnected());
        status.put("hasMcpTools", aiChatService.hasMcpTools());
        status.put("sending", aiChatService.isSending());
        status.put("server", mcpClientService.getActiveServerName());
        return Response.ok(status).build();
    }

    @POST
    @Path("/message")
    public Response sendMessage(ChatMessageRequestDTO request) {
        try {
            if (!aiChatService.isEnabled()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(new ErrorResponse("AI chat is disabled. Set ai.chat.enabled=true."))
                        .build();
            }
            if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Request body with 'message' is required"))
                        .build();
            }

            String sessionId = (request.getSessionId() == null || request.getSessionId().isBlank())
                    ? "default"
                    : request.getSessionId().trim();

            String answer = aiChatService.sendMessage(sessionId, request.getMessage());
            return Response.ok(new ChatResponseDTO(sessionId, answer)).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.errorf("Error processing chat message: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to process chat message: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/history")
    public Response getHistory(@QueryParam("sessionId") String sessionId) {
        try {
            String id = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId.trim();
            List<ChatHistoryEntryDTO> history = aiChatService.getHistory(id);
            return Response.ok(history).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.errorf("Error loading chat history: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to load chat history: " + e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/history")
    public Response clearHistory(@QueryParam("sessionId") String sessionId) {
        try {
            String id = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId.trim();
            if ("*".equals(id)) {
                aiChatService.clearAllHistory();
                return Response.ok(new SuccessResponse("All chat history cleared")).build();
            }
            aiChatService.clearHistory(id);
            return Response.ok(new SuccessResponse("Chat history cleared for session " + id)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.errorf("Error clearing chat history: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to clear chat history: " + e.getMessage()))
                    .build();
        }
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
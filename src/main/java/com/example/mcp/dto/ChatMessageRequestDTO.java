package com.example.mcp.dto;

/**
 * DTO for AI chat message requests.
 */
public class ChatMessageRequestDTO {
    private String sessionId;
    private String message;

    public ChatMessageRequestDTO() {
    }

    public ChatMessageRequestDTO(String sessionId, String message) {
        this.sessionId = sessionId;
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
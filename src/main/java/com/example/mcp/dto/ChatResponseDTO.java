package com.example.mcp.dto;

/**
 * DTO for AI chat responses.
 */
public class ChatResponseDTO {
    private String sessionId;
    private String answer;

    public ChatResponseDTO() {
    }

    public ChatResponseDTO(String sessionId, String answer) {
        this.sessionId = sessionId;
        this.answer = answer;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
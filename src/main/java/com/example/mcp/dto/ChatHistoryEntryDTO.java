package com.example.mcp.dto;

/**
 * Single chat history entry (role + content).
 */
public class ChatHistoryEntryDTO {
    private String role;
    private String content;

    public ChatHistoryEntryDTO() {
    }

    public ChatHistoryEntryDTO(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
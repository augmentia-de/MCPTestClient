package com.example.mcp.dto;

import java.util.Map;

/**
 * DTO for tool call requests.
 */
public class ToolCallRequestDTO {
    private Map<String, Object> arguments;

    public ToolCallRequestDTO() {
    }

    public ToolCallRequestDTO(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}

package com.example.mcp.dto;

import java.util.Map;

/**
 * DTO for tool information.
 */
public class ToolDTO {
    private String name;
    private String description;
    private Map<String, Object> inputSchema;

    public ToolDTO() {
    }

    public ToolDTO(String name, String description, Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }
}

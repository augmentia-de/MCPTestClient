package com.example.mcp.dto;

import java.util.List;

/**
 * DTO for tool call responses.
 */
public class ToolCallResponseDTO {
    private boolean isError;
    private List<ContentDTO> content;

    public ToolCallResponseDTO() {
    }

    public ToolCallResponseDTO(boolean isError, List<ContentDTO> content) {
        this.isError = isError;
        this.content = content;
    }

    public boolean isError() {
        return isError;
    }

    public void setError(boolean error) {
        isError = error;
    }

    public List<ContentDTO> getContent() {
        return content;
    }

    public void setContent(List<ContentDTO> content) {
        this.content = content;
    }

    public static class ContentDTO {
        private String type;
        private String text;

        public ContentDTO() {
        }

        public ContentDTO(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}

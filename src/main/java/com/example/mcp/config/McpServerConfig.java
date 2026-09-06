package com.example.mcp.config;

public class McpServerConfig {
    private String name;
    private String url;
    private String bearerToken;
    private String transport = "STREAMABLE_HTTP";

    public McpServerConfig() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getBearerToken() { return bearerToken; }
    public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }
    public String getTransport() { return transport; }
    public void setTransport(String transport) { this.transport = transport; }
}
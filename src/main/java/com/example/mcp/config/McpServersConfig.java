package com.example.mcp.config;

import java.util.List;

public class McpServersConfig {
    private List<McpServerConfig> servers;

    public McpServersConfig() {}

    public List<McpServerConfig> getServers() { return servers; }
    public void setServers(List<McpServerConfig> servers) { this.servers = servers; }
}
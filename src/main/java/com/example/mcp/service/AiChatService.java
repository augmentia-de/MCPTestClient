package com.example.mcp.service;

import com.example.mcp.dto.ChatHistoryEntryDTO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional AI chat agent. When enabled, provides an LLM-backed chat that can
 * use the currently connected MCP server through a LangChain4j McpToolProvider.
 * Chat history is kept per session id.
 */
@ApplicationScoped
public class AiChatService {

    private static final Logger LOG = Logger.getLogger(AiChatService.class);

    private static final int MAX_SESSIONS = 100;

    public interface Assistant {
        @SystemMessage("""
                You are a helpful assistant. If MCP tools are available you can use them when needed \
                (e.g. browser automation, file access, calculations). Prefer the tools over guessing. \
                Keep answers concise and factual.""")
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @Inject
    ChatModel chatModel;

    @Inject
    McpClientService mcpClientService;

    @ConfigProperty(name = "ai.chat.enabled", defaultValue = "false")
    boolean chatEnabled;

    @ConfigProperty(name = "ai.chat.memory.max-messages", defaultValue = "20")
    int maxMessages;

    private final AtomicBoolean sending = new AtomicBoolean(false);

    private final Map<String, ChatMemory> memories = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ChatMemory> eldest) {
            return size() > MAX_SESSIONS;
        }
    };

    public boolean isEnabled() {
        return chatEnabled;
    }

    public boolean isSending() {
        return sending.get();
    }

    public boolean hasMcpTools() {
        return mcpClientService.getMcpClient() != null;
    }

    public String sendMessage(String sessionId, String message) {
        if (!chatEnabled) {
            throw new IllegalStateException("AI chat is disabled. Set ai.chat.enabled=true in application.properties.");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        if (!sending.compareAndSet(false, true)) {
            throw new IllegalStateException("Another chat request is already in progress");
        }

        try {
            Assistant assistant = buildAssistant();
            String answer = assistant.chat(sessionId, message.trim());
            LOG.infof("AI chat: session=%s, received answer (%d chars)", sessionId, answer.length());
            return answer;
        } finally {
            sending.set(false);
        }
    }

    public void clearHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        ChatMemory memory = memories.get(sessionId);
        if (memory != null) {
            memory.clear();
            LOG.infof("AI chat: cleared history for session=%s", sessionId);
        }
    }

    public void clearAllHistory() {
        memories.clear();
    }

    public List<ChatHistoryEntryDTO> getHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        ChatMemory memory = memories.get(sessionId);
        if (memory == null) {
            return List.of();
        }
        List<ChatHistoryEntryDTO> entries = new ArrayList<>();
        for (ChatMessage msg : memory.messages()) {
            entries.add(new ChatHistoryEntryDTO(roleOf(msg), textOf(msg)));
        }
        return entries;
    }

    private Assistant buildAssistant() {
        AiServices<Assistant> builder = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> memories.computeIfAbsent(
                        String.valueOf(memoryId),
                        id -> MessageWindowChatMemory.builder().maxMessages(maxMessages).build()));

        McpClient mcpClient = mcpClientService.getMcpClient();
        if (mcpClient != null) {
            McpToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClients(mcpClient)
                    .build();
            builder.toolProvider(toolProvider);
            LOG.debug("AI chat: MCP tool provider attached for active server");
        } else {
            LOG.debug("AI chat: no MCP connection, chat running without tools");
        }

        return builder.build();
    }

    private static String roleOf(ChatMessage message) {
        return switch (message.type()) {
            case USER -> "user";
            case AI -> "assistant";
            case SYSTEM -> "system";
            case TOOL_EXECUTION_RESULT -> "tool";
            case CUSTOM -> "custom";
        };
    }

    private static String textOf(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.UserMessage userMessage) {
            if (userMessage.hasSingleText()) {
                return userMessage.singleText();
            }
            return String.valueOf(userMessage.contents());
        }
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text() != null ? aiMessage.text() : "(tool call)";
        }
        if (message instanceof dev.langchain4j.data.message.SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof ToolExecutionResultMessage toolResult) {
            return toolResult.text();
        }
        return message.toString();
    }
}
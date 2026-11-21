package com.project.util;

import com.project.dto.Tool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
public class ToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(ToolProvider.class);

    private final List<McpSyncClient> mcpSyncClients;
    private volatile Map<String, ToolCallback> toolMap = Map.of();
    private volatile List<Tool> tools = List.of();

    public ToolProvider(List<McpSyncClient> mcpSyncClients) {
        this.mcpSyncClients = mcpSyncClients;
    }

    @PostConstruct
    void init() {
        Map<String, ToolCallback> map = new LinkedHashMap<>();
        List<Tool> list = new ArrayList<>();

        for (McpSyncClient client : mcpSyncClients) {
            try {
                String server = client.getServerInfo().name();
                for (McpSchema.Tool t : client.listTools().tools()) {
                    Tool tool = new Tool(t.name(), server, firstSentence(t.description()));
                    map.put(tool.key(), SyncMcpToolCallback.builder()
                            .mcpClient(client).tool(t).build());
                    list.add(tool);
                }
            } catch (Exception e) {
                logger.error("Error loading tools from MCP client", e);
            }
        }
        this.toolMap = Collections.unmodifiableMap(map);
        this.tools = List.copyOf(list);
    }

    public List<Tool> getTools() {
        return tools;
    }

    public List<ToolCallback> getToolCallBack(List<String> toolKeys) {
        return toolKeys.stream()
                .map(toolMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String firstSentence(String d) {
        if (StringUtils.isEmpty(d)) return "";
        String line = d.strip().split("\\R", 2)[0];
        int dot = line.indexOf(". ");
        return dot > 0 ? line.substring(0, dot + 1) : line;
    }
}

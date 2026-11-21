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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                String serverName = client.getServerInfo().name();
                for (McpSchema.Tool inputTool : client.listTools().tools()) {
                    McpSchema.Tool clean = sanitize(inputTool);
                    Tool tool = new Tool(clean.name(), serverName, firstSentence(clean.description()));
                    map.put(tool.key(), SyncMcpToolCallback.builder()
                            .mcpClient(client)
                            .tool(clean)
                            .build());
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

    private static McpSchema.Tool sanitize(McpSchema.Tool tool) {
        Map<String, Object> schema = tool.inputSchema();
        if (schema == null) return tool;
        return new McpSchema.Tool(
                tool.name(),
                tool.title(),
                tool.description(),
                cleanMap(schema),
                tool.outputSchema(),
                tool.annotations(),
                tool.meta(),
                tool.icons());
    }

    private static Map<String, Object> cleanMap(Map<String, Object> in) {
        if (in == null) return null;

        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();

            if ("default".equals(key) && val == null) continue;

            if ("type".equals(key) && val instanceof List<?> types) {
                String picked = types.stream()
                        .map(String::valueOf)
                        .filter(x -> !"null".equals(x))
                        .findFirst()
                        .orElse("string");
                out.put("type", picked);
                continue;
            }

            out.put(key, cleanValue(val));
        }

        for (String combiner : List.of("anyOf", "oneOf")) {
            if (out.get(combiner) instanceof List<?> branches) {
                List<Object> kept = new ArrayList<>();
                for (Object b : branches) {
                    if (b instanceof Map<?, ?> m && "null".equals(m.get("type"))) continue;
                    kept.add(b);
                }

                if (kept.size() == 1 && kept.get(0) instanceof Map<?, ?> only) {
                    out.remove(combiner);
                    for (Map.Entry<?, ?> en : only.entrySet()) {
                        out.putIfAbsent(String.valueOf(en.getKey()), en.getValue());
                    }
                } else {
                    out.put(combiner, kept);
                }
            }
        }
        return out;
    }

    private static Object cleanValue(Object v) {
        if (v instanceof Map<?, ?> m) return cleanMap((Map<String, Object>) m);
        if (v instanceof List<?> l) return l.stream().map(ToolProvider::cleanValue).toList();
        return v;
    }

    private static String firstSentence(String d) {
        if (d == null || d.isBlank()) return "";
        String line = d.strip().split("\\R", 2)[0];
        int dot = line.indexOf(". ");
        return dot > 0 ? line.substring(0, dot + 1) : line;
    }
}
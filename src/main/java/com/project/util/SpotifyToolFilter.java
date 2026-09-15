package com.project.util;

import com.project.dto.LLMResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;


@Service
public class SpotifyToolFilter {

    public List<ToolCallback> getPlayRequestTools(ToolCallbackProvider provider, LLMResponse llmResponse) {
        return Arrays.stream(provider.getToolCallbacks())
                .filter(tc -> llmResponse.result().contains(tc.getToolDefinition().name()))
                .toList();
    }
}
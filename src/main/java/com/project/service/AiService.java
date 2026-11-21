package com.project.service;

import com.project.dto.LLMResponse;
import com.project.util.Constants;
import com.project.util.RateLimiter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final ChatClient secondaryAgent;
    private final ChatClient primaryAgent;
    private final RateLimiter rateLimiter;


    public AiService(@Qualifier("secondaryAgent") ChatClient secondaryAgent, @Qualifier("primaryAgent") ChatClient primaryAgent, RateLimiter rateLimiter) {
        this.secondaryAgent = secondaryAgent;
        this.primaryAgent = primaryAgent;
        this.rateLimiter = rateLimiter;
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "getResponseFromSecondaryAiAgent")
    public LLMResponse getResponseFromAiAgent(String query, String prompt) {
        try {
            if (rateLimiter.consume(query)) {
                return executeLLMCall(primaryAgent, query, prompt, LLMResponse.class);
            }
        } catch (Exception e) {
            logger.error("Error in getting LLM Response from Primary Agent{}", e.getMessage());
        }
        throw new RuntimeException("Error in processing request");
    }

    public LLMResponse getResponseFromSecondaryAiAgent(String query, String prompt, Throwable t) {
        try {
            return executeLLMCall(secondaryAgent, query, prompt, LLMResponse.class);
        } catch (Exception e) {
            logger.error("Error in getting LLM Response from Secondary Agent{}", e.getMessage());
        }
        return LLMResponse.defaultResponse();
    }

    public LLMResponse performToolCalling(String query, List<ToolCallback> tools) {
        try {
            if (rateLimiter.consume(query)) {
                return executeLLMCall(primaryAgent, query, Constants.Prompts.TOOL_EXECUTION_SYSTEM_INSTRUCTION, tools, LLMResponse.class);
            }
        } catch (Exception e) {
            logger.info("Error in performing tool calling {}", e.getMessage());
        }
        return LLMResponse.defaultResponse();
    }

    public <T> T executeLLMCall(String query, String prompt, Class<T> returnType) {
        try {
            if (rateLimiter.consume(query)) {
                return executeLLMCall(primaryAgent, query, prompt, null, returnType);
            }
        } catch (Exception e) {
            logger.error("Error in executing LLM call {}", e.getMessage());
        }
        return null;
    }

    public <T> T executeLLMCall(ChatClient chatClient, String query, String prompt, Class<T> returnType) {
        return executeLLMCall(chatClient, query, prompt, null, returnType);
    }


    public <T> T executeLLMCall(ChatClient chatClient, String query, String prompt, List<ToolCallback> tools, Class<T> returnType) {
        var promptSpec = chatClient.prompt()
                .system(prompt)
                .user(query);

        if (tools != null && !tools.isEmpty()) {
            promptSpec = promptSpec.tools(tools);
        }
        if (String.class.equals(returnType)) {
            return returnType.cast(promptSpec.call().content());
        }
        return promptSpec.call().entity(returnType);
    }
}

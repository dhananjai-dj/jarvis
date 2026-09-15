package com.project.service;

import com.project.dto.LLMResponse;
import com.project.util.Constants;
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

    public AiService(@Qualifier("secondaryAgent") ChatClient secondaryAgent, @Qualifier("primaryAgent") ChatClient primaryAgent) {
        this.secondaryAgent = secondaryAgent;
        this.primaryAgent = primaryAgent;
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "getResponseFromSecondaryAiAgent")
    public LLMResponse getResponseFromAiAgent(String query, String prompt) {
        try {
            String llmResponseString = primaryAgent.prompt().system(prompt).user(query).call().content();
            return LLMResponse.parseString(llmResponseString);
        } catch (Exception e) {
            logger.error("Error in getting LLM Response from Primary Agent{}", e.getMessage());
            throw e;
        }
    }

    public LLMResponse getResponseFromSecondaryAiAgent(String query, String prompt, Throwable t) {
        try {
            String llmResponseString = secondaryAgent.prompt().system(prompt).user(query).call().content();
            return LLMResponse.parseString(llmResponseString);
        } catch (Exception e) {
            logger.error("Error in getting LLM Response from Secondary Agent{}", e.getMessage());
        }
        return new LLMResponse("Error in generating summary", false, true);
    }

    public String performToolCalling(String query, List<ToolCallback> tools) {
        try {
            return primaryAgent.prompt().system(Constants.Prompts.TOOL_EXECUTION_SYSTEM_INSTRUCTION).user("Paro by Aditya Rikhari").tools(tools).call().content();
        } catch (Exception e) {
            logger.info("Error in performing tool calling {}", e.getMessage());
        }
        return "Error in performing the action. Try again later";
    }
}

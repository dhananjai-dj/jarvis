package com.project.service;

import com.project.dto.LLMResponse;
import com.project.util.Constants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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
    public LLMResponse getResponseFromAiAgent(String query) {
        try {
            String llmResponseString = primaryAgent.prompt().system(Constants.Prompts.PRIMARY_AGENT_SYSTEM_INSTRUCTION).user(query).call().content();
            return LLMResponse.parseString(llmResponseString);
        } catch (Exception e) {
            logger.error("Error in getting LLM Response from Primary Agent{}", e.getMessage());
        }
        return LLMResponse.defaultResponse();
    }

    public LLMResponse getResponseFromSecondaryAiAgent(String query) {
        try {
            String llmResponseString = secondaryAgent.prompt().system(Constants.Prompts.SECONDARY_AGENT_SYSTEM_INSTRUCTION).user(query).call().content();
            return LLMResponse.parseString(llmResponseString);
        } catch (Exception e) {
            logger.error("Error in getting LLM Response from Secondary Agent{}", e.getMessage());
        }
        return LLMResponse.defaultResponse();
    }

    public String getResponseFromLocalAiAgent(String query, String prompt) {
        try {
            return secondaryAgent.prompt().system(prompt).user(query).call().content();
        } catch (Exception e) {
            logger.error("Error in getting response from Secondary Agent{}", e.getMessage());
        }
        return "Error in generating summary";
    }

}

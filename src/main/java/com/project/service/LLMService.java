package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.dto.LLMResponse;
import com.project.util.Constants;
import com.project.util.ParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LLMService {

    private static final Logger logger = LoggerFactory.getLogger(LLMService.class);

    private final ChatClient secondaryAgent;
    private final ChatClient primaryAgent;
    private final ConversationService conversationService;

    public LLMService(@Qualifier("secondaryAgent") ChatClient secondaryAgent, @Qualifier("primaryAgent") ChatClient primaryAgent, ConversationService conversationService) {
        this.secondaryAgent = secondaryAgent;
        this.primaryAgent = primaryAgent;
        this.conversationService = conversationService;
    }

    public LLMResponse processUserQuery(String finalQuery) {
        try {
            LLMResponse primaryAgentResponse = getLLMResponseFromPrimaryAgent(finalQuery);
            return primaryAgentResponse.isError() ? getLLMResponseFromSecondaryAgent(finalQuery) : primaryAgentResponse;
        } catch (Exception e) {
            logger.error("Error in processing User query {}", e.getMessage());
        }
        return LLMResponse.defaultResponse();
    }

    public String getSessionSummary(String sessionId) {
        String result = "Unable to save session";
        try {
            List<Conversation> conversationList = conversationService.getAllConversationOfTheSession(sessionId);
            if (conversationList != null) {
                String conversationString = ParserUtil.parseConversationListToString(conversationList);
                result = secondaryAgent.prompt().system(Constants.Prompts.SUMMARY_INSTRUCTION).user(conversationString).call().content();
            }
        } catch (Exception e) {
            logger.error("Error in generating summary for the session with session Id {}", sessionId);
        }
        return result;
    }

    private LLMResponse getLLMResponseFromPrimaryAgent(String query) {
        try {
            String llmResponseString = primaryAgent.prompt().system(Constants.Prompts.PRIMARY_AGENT_SYSTEM_INSTRUCTION).user(query).call().content();
            logger.info(llmResponseString);
            return LLMResponse.parseString(llmResponseString);
        } catch (Exception e) {
            logger.error("Error in getting LLM Response from Primary Agent{}", e.getMessage());
        }
        return LLMResponse.defaultResponse();
    }

    private LLMResponse getLLMResponseFromSecondaryAgent(String query) {
        try {
            logger.info("Trying to call secondary Agent {}", query);
            String llmResponseString = secondaryAgent.prompt().system(Constants.Prompts.SECONDARY_AGENT_SYSTEM_INSTRUCTION).user(query).call().content();
            LLMResponse secondaryAgentRespnose = LLMResponse.parseString(llmResponseString);
            logger.info("Secondary LLM API returned successfully with response {}", secondaryAgentRespnose);
            return secondaryAgentRespnose;
        } catch (Exception e) {
            logger.error("Error in getting LLM Response from Secondary Agent{}", e.getMessage());
        }
        return LLMResponse.defaultResponse();
    }





}

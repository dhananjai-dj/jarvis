package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.dao.entities.Role;
import com.project.dao.repositories.ConversationRepository;
import com.project.dto.JarvisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final CacheService cacheService;
    private final Logger logger = LoggerFactory.getLogger(ConversationService.class);

    public ConversationService(ConversationRepository conversationRepository, CacheService cacheService) {
        this.conversationRepository = conversationRepository;
        this.cacheService = cacheService;
    }

    public void saveConversation(String sessionId, JarvisResponse jarvisResponse){
        Conversation conversation = Conversation.builder().isLLMSuccess(jarvisResponse.isError()).message(jarvisResponse.result()).role(Role.ASSISTANT).sessionId(sessionId).build();
        saveConversation(conversation);
    }

    public void saveConversation(Conversation conversation) {
        try {
            conversationRepository.save(conversation);
            Conversation conversationSummary = cacheService.putToCache(conversation);
            if(conversationSummary != null) {
                conversationRepository.save(conversationSummary);
            }
        } catch (Exception e) {
            logger.error("Error in saving the conversation {}", e.getMessage());
        }
    }

    public List<Conversation> getAllConversationOfTheSession(String sessionId) {
        try {
            return conversationRepository.findAllBySessionId(sessionId);
        } catch (Exception e) {
            logger.error("Error in fetching the conversation by session Id");
        }
        return null;
    }

    public List<Conversation> getAllConversation() {
        try {
            return conversationRepository.findAll();
        } catch (Exception e) {
            logger.error("Error in fetching all conversation");
        }
        return null;
    }
}

package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.dao.repositories.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public void saveConversation(Conversation conversation) {
        try {
            conversationRepository.save(conversation);
            cacheService.putToCache(conversation);
        } catch (Exception e) {
            logger.error("Error in saving the conversation {}", e.getMessage());
        }
    }

    public List<Conversation> getPreviousConversations(String sessionId) {
        try {
            return conversationRepository.findTop10BySessionIdOrderByIdDesc(sessionId);
        } catch (Exception e) {
            logger.error("Error in fetching the previous conversation {}", e.getMessage());
        }
        return new ArrayList<>();
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

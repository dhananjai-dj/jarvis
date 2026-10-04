package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.util.Constants;
import com.project.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CacheService {
    private final HashOperations<String, String, List<Conversation>> cacheMap;
    private final Logger logger = LoggerFactory.getLogger(CacheService.class);

    private final AiService aiService;


    public CacheService(RedisTemplate<String, Object> redisTemplate, AiService aiService) {
        this.cacheMap = redisTemplate.opsForHash();
        this.aiService = aiService;
    }

    public Conversation putToCache(Conversation conversation) {
        try {
            String sessionId = conversation.getSessionId();
            List<Conversation> conversationList = getSessionConversation(sessionId);
            conversationList.add(conversation);
            if (conversationList.size() > 20) {
                String combinedConversationString = StringUtils.parseConversationListToString(conversationList);
                Conversation combinedConversation = aiService.executeLLMCall(combinedConversationString, Constants.Prompts.CONVERSATION_SUMMARY_INSTRUCTION, Conversation.class);
                cacheMap.put(Constants.CONVERSATION_CACHE_KEY, sessionId, List.of(combinedConversation));
                return combinedConversation;
            } else {
                cacheMap.put(Constants.CONVERSATION_CACHE_KEY, sessionId, conversationList);
            }
        } catch (Exception e) {
            logger.error("Error in putting into cache for the session {} and the conversation {} with error {}", conversation.getSessionId(), conversation, e.getMessage());
        }
        return null;
    }

    public List<Conversation> getSessionConversation(String sessionId) {
        try {
            List<Conversation> list = cacheMap.get(Constants.CONVERSATION_CACHE_KEY, sessionId);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error in fetching the conversation for this session {} with error {}", sessionId, e.getMessage());
        }
        return new ArrayList<>();
    }
}

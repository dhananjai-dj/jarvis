package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.util.Constants;
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


    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.cacheMap = redisTemplate.opsForHash();
    }

    public void putToCache(Conversation conversation) {
        try {
            String sessionId = conversation.getSessionId();
            List<Conversation> combinedConversationList;
            List<Conversation> previousConversationList = getSessionConversation(sessionId);
            if (previousConversationList.size() > 5) {
                combinedConversationList = new ArrayList<>(previousConversationList.subList(previousConversationList.size() - 5, previousConversationList.size()));
            } else {
                combinedConversationList = new ArrayList<>(previousConversationList);
            }
            combinedConversationList.add(conversation);
            cacheMap.put(Constants.CONVERSATION_CACHE_KEY, sessionId, combinedConversationList);
        } catch (Exception e) {
            logger.error("Error in putting into cache for the session {} and the conversation {} with error {}", conversation.getSessionId(), conversation, e.getMessage());
        }
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

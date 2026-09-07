package com.project.service;

import com.project.util.Prompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String search(String input) {
        logger.info("Sending request to LLM");
        String result = "Unable to find";
        try {
            result = chatClient.prompt().system(Prompts.SYSTEM_INSTRUCTION).user(input).call().content();
        } catch (Exception e) {
            logger.error("Error in searching {}", e.getMessage());
        }
        return result;
    }
}

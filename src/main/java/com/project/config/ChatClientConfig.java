package com.project.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {


    @Bean(name = "primaryAgent")
    public ChatClient getChatClient(@Qualifier("googleGenAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(ChatOptions.builder()
                        .maxTokens(800))
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }


    @Bean(name = "secondaryAgent")
    public ChatClient getLocalChatClient(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(ChatOptions.builder()
                        .maxTokens(800))
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}

package com.project.config;

import com.openai.core.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ChatClientConfig {


    @Bean(name = "primaryAgent")
    public ChatClient getChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(ChatOptions.builder()
                        .maxTokens(800))
                .build();
    }


    @Bean(name = "secondaryAgent")
    public ChatClient getLocalChatClient() {
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .baseUrl("http://192.168.1.7:1234/v1")
                .apiKey("lm-studio")
                .model("local-model")
                .build();
        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .options(chatOptions)
                .httpClientBuilderCustomizer(builder -> builder
                        .timeout(Timeout
                                .builder()
                                .connect(Duration.ofSeconds(5))
                                .read(Duration.ofSeconds(30))
                                .build()))
                .build();
        return ChatClient.builder(openAiChatModel).build();
    }
}

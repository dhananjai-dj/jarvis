package com.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class TranscriberConfig {
    @Value("${transcriber.baseUrl:}")
    private String baseUrl;
    @Value("${transcriber.key:}")
    private String apiKey;

    @Bean(name = "Transcriber")
    public RestClient getTranscriber() {
        return RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + apiKey).build();
    }
}

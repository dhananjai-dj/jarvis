package com.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Value("${groq.baseUrl:}")
    private String baseUrl;
    @Value("${groq.key:}")
    private String apiKey;

    @Bean(name = "groqRestClient")
    public RestClient getRestClient() {
        return RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + apiKey).build();
    }
}

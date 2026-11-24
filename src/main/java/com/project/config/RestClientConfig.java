package com.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Value("${groq.baseUrl:}")
    private String groqBaseUrl;
    @Value("${groq.key:}")
    private String groqApiKey;

    @Value("${jena.baseUrl:}")
    private String jenaBaseUrl;
    @Value("${jena.key:}")
    private String jenaApiKey;

    @Bean(name = "groqRestClient")
    public RestClient getGroqRestClient() {
        return buildRestClient(groqBaseUrl, groqApiKey);
    }

    @Bean(name = "jenaRestClient")
    public RestClient getJenaRestClient() {
        return buildRestClient(jenaBaseUrl, jenaApiKey);
    }

    private RestClient buildRestClient(String baseUrl, String apiKey) {
        return RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + apiKey).build();
    }
}

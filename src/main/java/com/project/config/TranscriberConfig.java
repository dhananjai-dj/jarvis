package com.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class TranscriberConfig {
    @Bean(name = "Transcriber")
    public RestClient getTranscriber(){
        return RestClient.builder().baseUrl("http://127.0.0.1:8180").build();
    }
}

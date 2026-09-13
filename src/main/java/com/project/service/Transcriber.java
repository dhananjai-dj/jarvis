package com.project.service;

import com.project.dto.WhisperResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.File;

@Service
public class Transcriber {

    private static final Logger logger = LoggerFactory.getLogger(Transcriber.class);

    private final RestClient transcriber;


    public Transcriber(@Qualifier("Transcriber") RestClient transcriber) {
        this.transcriber = transcriber;
    }

    public String transcribe(File wavFile) {
        String result = "";
        try {
            if (wavFile == null) {
                return "Unable to find the file to transcribe";
            }
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(wavFile));
            body.add("response_format", "json");
            WhisperResponse response = transcriber.post()
                    .uri("/inference")
                    .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(WhisperResponse.class);
            result = response != null ? response.text() : "";
        } catch (Exception e) {
            logger.error("Error in transcribing the file {} because {}", wavFile.getName(), e.getMessage());
        }
        return result;
    }
}

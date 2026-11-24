package com.project.config;

import com.project.dto.EmbeddingResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Component
public class JenaEmbeddingModel extends AbstractEmbeddingModel {

    @Value("${jena.model:}")
    private String model;
    @Value("${jena.dimension:}")
    private int dimension;
    @Value("${jena.endPoint:}")
    private String endPoint;

    private static final int BATCH = 64;
    private final RestClient restClient;


    private static final Logger logger = LoggerFactory.getLogger(JenaEmbeddingModel.class);

    public JenaEmbeddingModel(@Qualifier("jenaRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public org.springframework.ai.embedding.@NonNull EmbeddingResponse call(@NonNull EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        try {
            List<String> instructions = request.getInstructions();
            int size = instructions.size();
            for (int i = 0; i < size; i += BATCH) {
                List<String> subList = instructions.subList(i, Math.min(size, i + 64));
                List<Embedding> batch = getEmbeddings(subList);
                if (batch.isEmpty()) {
                    return new org.springframework.ai.embedding.EmbeddingResponse(batch);
                }
                embeddings.addAll(batch);
            }
        } catch (Exception e) {
            logger.error("Error in calling Embedding Model {}", e.getMessage());
        }
        return new org.springframework.ai.embedding.EmbeddingResponse(embeddings);
    }

    @Override
    public float @NonNull [] embed(Document document) {
        assert document.getText() != null;
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return dimension;
    }

    private List<Embedding> getEmbeddings(List<String> content) {
        try {
            EmbeddingResponse embeddingResponse = restClient.post().uri(endPoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model,
                            "task", "retrieval.passage",
                            "instructions", content))
                    .retrieve().body(EmbeddingResponse.class);
            if (embeddingResponse == null) {
                return List.of();
            }
            List<EmbeddingResponse.EmbeddingData> embeddingDataList = embeddingResponse.data();
            if (embeddingDataList == null || embeddingDataList.isEmpty()) {
                return List.of();
            }
            return embeddingDataList.stream().map(k -> new Embedding(k.embedding(), k.index())).toList();
        } catch (Exception e) {
            logger.error("Error in embedding the content {} got error {}", content, e.getMessage());
        }
        return List.of();
    }
}

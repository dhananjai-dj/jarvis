package com.project.dto;

import java.util.List;

public record EmbeddingResponse(String model, String object, Usage usage, List<EmbeddingData> data) {
    public record Usage(int total_tokens) {
    }

    public record EmbeddingData(String object, int index, float[] embedding) {
    }
}

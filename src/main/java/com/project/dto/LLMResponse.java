package com.project.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public record LLMResponse(String result, boolean isToolCallingRequired, boolean isError) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static LLMResponse defaultResponse() {
        return new LLMResponse("Unable to Process at the moment", false, true);
    }

    public static LLMResponse parseString(String llmResult) {
        if (llmResult == null || llmResult.isBlank()) {
            return defaultResponse();
        }
        try {
            int start = llmResult.indexOf('{');
            int end = llmResult.lastIndexOf('}');
            if (start == -1 || end <= start) {
                return defaultResponse();
            }
            String jsonSubstring = llmResult.substring(start, end + 1);
            return OBJECT_MAPPER.readValue(jsonSubstring, LLMResponse.class);

        } catch (Exception e) {
            return defaultResponse();
        }
    }
}

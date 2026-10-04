package com.project.dto;

public record LLMResponse(String result, boolean isToolCallingRequired, boolean isError) {

    public static LLMResponse defaultResponse() {
        return new LLMResponse("Unable to Process at the moment", false, true);
    }

}

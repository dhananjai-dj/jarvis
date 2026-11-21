package com.project.dto;

public record LLMResponse(String result, boolean isToolCallingRequired, boolean isError) {

    public static LLMResponse defaultResponse() {
        return new LLMResponse("I'm afraid something has gone awry, sir. Might you try that once more?", false, true);
    }

}

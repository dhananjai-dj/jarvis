package com.project.dto;

public record JarvisResponse(String result, boolean isToolCallingRequired, boolean isError) {

    public static JarvisResponse defaultResponse() {
        return new JarvisResponse("I'm afraid something has gone awry, sir. Might you try that once more?", false, true);
    }

}

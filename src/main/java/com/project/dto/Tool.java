package com.project.dto;

public record Tool(String name, String mcpName, String description) {
    public String key(){
        return mcpName + "_" + name;
    }
}


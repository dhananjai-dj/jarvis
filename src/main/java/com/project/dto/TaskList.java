package com.project.dto;

import java.util.List;

public record TaskList(List<Task> tasks) {

    public static TaskList getSingleTask(String userQuery) {
        Task task = new Task("NONE", userQuery);
        return new TaskList(List.of(task));
    }

    public record Task(String mcpName, String description) {
    }
}


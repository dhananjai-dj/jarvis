package com.project.service;

import com.project.dto.*;
import com.project.util.Constants;
import com.project.util.StringUtils;
import com.project.util.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QueryExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(QueryExecutionService.class);
    private static final String NO_TOOL_REPLY =
            "I'm afraid I have no means of doing that just yet, sir.";

    private final AiService aiService;
    private final ToolProvider toolProvider;


    public QueryExecutionService(AiService aiService, ToolProvider toolProvider) {
        this.aiService = aiService;
        this.toolProvider = toolProvider;
    }

    public JarvisResponse processUserQuery(String combined, String userQuery, String history) {
        JarvisResponse result;
        try {
            JarvisResponse first = aiService.getResponseFromAiAgent(combined, Constants.Prompts.PRIMARY_AGENT_SYSTEM_INSTRUCTION);
            if (!first.isToolCallingRequired()) {
                result = first;
            } else {
                result = runActionPlan(history, userQuery, combined);
            }
        } catch (Exception e) {
            logger.error("Error in processing user query", e);
            result = JarvisResponse.defaultResponse();
        }
        return result;
    }

    private JarvisResponse runActionPlan(String history, String userQuery, String combined) {
        TaskList taskList = planTasks(combined, userQuery);
        List<Task> tasks = taskList.tasks();
        logger.info("Action plan has {} step(s): {}", tasks.size(), tasks);

        List<StepResult> done = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            JarvisResponse response = executeStep(history, task.description(), done, task.mcpName());
            done.add(new StepResult(task, response));
            logger.info("Step {}/{} [{}] -> {}", i + 1, tasks.size(), task, response);
            if (response.isError()) {
                break;
            }
        }
        return composeReply(userQuery, tasks, done);
    }

    private TaskList planTasks(String combined, String userQuery) {
        try {
            Set<String> serverName = toolProvider.getTools().keySet();
            return aiService.executeLLMCall(combined, Constants.Prompts.TASK_PLANNER.formatted(serverName), TaskList.class);
        } catch (Exception e) {
            logger.error("Error in planning tasks, falling back to a single step", e);
        }
        return TaskList.getSingleTask(userQuery);
    }

    private JarvisResponse executeStep(String history, String task, List<StepResult> done, String mcpName) {
        try {
            String routerQuery = combine(history, task);
            List<ToolCallback> tools = getToolsToPerformAction(routerQuery, mcpName);

            if (tools.isEmpty()) {
                JarvisResponse chat = aiService.getResponseFromAiAgent(routerQuery, Constants.Prompts.PRIMARY_AGENT_SYSTEM_INSTRUCTION);
                return chat.isToolCallingRequired() ? new JarvisResponse(NO_TOOL_REPLY, true, true) : chat;
            }

            String executionQuery = combine(history, task + stepContext(done));
            ToolRunResult toolRunResult = aiService.performToolCalling(executionQuery, tools);
            logger.info("Tool result {}", toolRunResult);
            return new JarvisResponse(toolRunResult.finalText(), true, toolRunResult.isFailed());
        } catch (Exception e) {
            logger.error("Error in executing step [{}]", task, e);
            return JarvisResponse.defaultResponse();
        }
    }

    private JarvisResponse composeReply(String userQuery, List<Task> tasks, List<StepResult> done) {
        if (tasks.size() == 1) {
            return done.getFirst().response();
        }
        boolean anyError = done.stream().anyMatch(s -> s.response().isError());
        String reply = null;
        try {
            StringBuilder input = new StringBuilder("User request: ").append(userQuery).append("\nStep results:\n");
            for (int i = 0; i < tasks.size(); i++) {
                input.append(i + 1).append(". Task: ").append(tasks.get(i)).append('\n');
                if (i < done.size()) {
                    JarvisResponse r = done.get(i).response();
                    input.append("   Status: ").append(r.isError() ? "FAILED OR NEEDS INPUT" : "DONE").append('\n');
                    input.append("   Report: ").append(r.result()).append('\n');
                } else {
                    input.append("   Status: NOT ATTEMPTED\n");
                }
            }
            reply = aiService.executeLLMCall(input.toString(), Constants.Prompts.REPLY_COMPOSER, String.class);
        } catch (Exception e) {
            logger.error("Error in composing the final reply", e);
        }
        if (StringUtils.isEmpty(reply)) {
            reply = done.stream().map(s -> s.response().result()).collect(Collectors.joining(" "));
        }
        return new JarvisResponse(reply.strip(), true, anyError);
    }

    private String stepContext(List<StepResult> done) {
        if (done.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n(Already completed earlier in this same request, for reference only, do not repeat:");
        for (int i = 0; i < done.size(); i++) {
            sb.append(' ').append(i + 1).append(") ").append(done.get(i).response().result());
        }
        return sb.append(')').toString();
    }

    private List<ToolCallback> getToolsToPerformAction(String query, String mcpName) {
        try {
            if (StringUtils.isEmpty(mcpName) || "NONE".equalsIgnoreCase(mcpName)) {
                return List.of();
            }
            String catalog = toolProvider.getTools(mcpName).stream()
                    .map(tool -> tool.name() + ": " + tool.description())
                    .collect(Collectors.joining("\n"));
            ToolIdentifier toolIdentifier = aiService.executeLLMCall(query, Constants.Prompts.TOOL_IDENTIFIER.formatted(catalog), ToolIdentifier.class);
            logger.info("Tool Identifier response {}", toolIdentifier);
            List<String> toolKeys = Arrays.stream(toolIdentifier.toolList().split(","))
                    .map(String::strip)
                    .filter(k -> !k.isEmpty())
                    .distinct()
                    .toList();
            return toolProvider.getToolCallBack(toolKeys, toolIdentifier.mcpName());
        } catch (Exception e) {
            logger.error("Error in identifying tool to perform action", e);
        }
        return List.of();
    }

    private String combine(String historyBlock, String currentMessage) {
        if (historyBlock == null || historyBlock.isEmpty()) {
            return currentMessage;
        }
        return historyBlock + "\n Current User message \n" + currentMessage;
    }
}

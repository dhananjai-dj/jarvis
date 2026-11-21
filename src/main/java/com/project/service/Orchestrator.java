package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.dao.entities.Role;
import com.project.dto.LLMResponse;
import com.project.dto.StepResult;
import com.project.util.Constants;
import com.project.util.PiiMasker;
import com.project.util.Transcriber;
import com.project.util.StringUtils;
import com.project.util.Synthesizer;
import com.project.util.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class Orchestrator {
    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);

    private static final int MAX_STEPS = 5;

    private static final Pattern COMPOUND_HINT =
            Pattern.compile("(?i)\\b(and|then|also|plus|after that|afterwards|as well)\\b|[,;]");

    private static final String GENERIC_FAILURE =
            "I'm afraid something has gone awry, sir. Might you try that once more?";
    private static final String NO_TOOL_REPLY =
            "I'm afraid I have no means of doing that just yet, sir.";

    private final File errorMessageFile = new File("error_message.wav");

    private final AiService aiService;
    private final Transcriber transcriber;
    private final Synthesizer synthesizer;
    private final CacheService cacheService;
    private final ToolProvider toolProvider;
    private final SessionService sessionService;
    private final ConversationService conversationService;
    private final RecordingService recordingService;


    public Orchestrator(Transcriber transcriber, AiService aiService, Synthesizer synthesizer, CacheService cacheService, SessionService sessionService, ToolProvider toolProvider, ConversationService conversationService, RecordingService recordingService) {
        this.aiService = aiService;
        this.transcriber = transcriber;
        this.synthesizer = synthesizer;
        this.cacheService = cacheService;
        this.sessionService = sessionService;
        this.toolProvider = toolProvider;
        this.conversationService = conversationService;
        this.recordingService = recordingService;
    }

    public void respond(File wav, String sessionId, String filePath, boolean isNewSession) {
        synthesizer.resetStop();
        Thread waitingThread = new Thread(
                () -> {
                    try {
                        Thread.sleep(5000);
                        synthesizer.playWaitingMessage();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
        );
        waitingThread.start();
        try {
            String userQuery = transcriber.transcribe(wav);
            if (StringUtils.isEmpty(userQuery)) {
                throw new RuntimeException("Empty transcriber response !!!");
            }
            userQuery = PiiMasker.maskValue(userQuery);
            if (isNewSession) {
                sessionService.createSession(sessionId, userQuery);
            }

            saveUserConversation(sessionId, userQuery, filePath + Constants.INPUT_RECORDING_FILE_NAME);

            LLMResponse llmOutput = handleUserQuery(sessionId, userQuery);
            logger.info("Output from ml: {}", llmOutput);

            String botReply = PiiMasker.unMaskValue(llmOutput.result());

            stopWaitingThread(waitingThread);

            File outputFile = synthesizer.synthesize(botReply, filePath);
            synthesizer.speak(outputFile);
        } catch (Exception e) {
            stopWaitingThread(waitingThread);
            synthesizer.speak(errorMessageFile);
            logger.error("Error in responding", e);
        }
    }

    public void startRecording(String sessionId, int conversationCount) {
        logger.info("Recording started for the session {} with conversation of count {}", sessionId, conversationCount);
        recordingService.startRecording(Constants.RECORDING_PATH_PREFIX + sessionId + "/" + conversationCount);
    }

    public File stopRecording(String sessionId, int conversationCount) {
        logger.info("Recording stopped for the session {} with conversation of count {}", sessionId, conversationCount);
        return recordingService.stopRecording();
    }

    public void stopSpeech() {
        synthesizer.stopSpeaking();
    }

    public void saveSummary(String sessionId) {
        try {
            String summary = getSessionSummary(sessionId);
            sessionService.saveSessionSummary(sessionId, summary);
        } catch (Exception e) {
            logger.error("Error in saving the summary", e);
        }
    }

    private LLMResponse handleUserQuery(String sessionId, String userQuery) {
        String history = getHistoryBlock(sessionId);
        String combined = combine(history, userQuery);
        logger.info("Combined user query {}", combined);
        LLMResponse result;
        try {
            LLMResponse first = aiService.getResponseFromAiAgent(combined, Constants.Prompts.PRIMARY_AGENT_SYSTEM_INSTRUCTION);
            if (!first.isToolCallingRequired()) {
                result = first;
            } else {
                result = runActionPlan(history, userQuery, combined);
            }
        } catch (Exception e) {
            logger.error("Error in processing user query", e);
            result = LLMResponse.defaultResponse();
        }
        conversationService.saveConversation(sessionId, result);
        return result;
    }

    private LLMResponse runActionPlan(String history, String userQuery, String combined) {
        List<String> tasks = planTasks(combined, userQuery);
        logger.info("Action plan has {} step(s): {}", tasks.size(), tasks);

        List<StepResult> done = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            String task = tasks.get(i);
            LLMResponse response = executeStep(history, task, done);
            done.add(new StepResult(task, response));
            logger.info("Step {}/{} [{}] -> {}", i + 1, tasks.size(), task, response);
            if (response.isError()) {
                break;
            }
        }
        return composeReply(userQuery, tasks, done);
    }

    private List<String> planTasks(String combined, String userQuery) {
        if (!COMPOUND_HINT.matcher(userQuery).find()) {
            return List.of(userQuery);
        }
        try {
            String raw = aiService.executeLLMCall(combined, Constants.Prompts.TASK_PLANNER, String.class);
            List<String> tasks = new ArrayList<>();
            if (raw != null) {
                for (String line : raw.split("\\R")) {
                    String task = line.strip().replaceFirst("^(?:[-*]|\\d+[.)])\\s*", "");
                    if (!task.isBlank()) {
                        tasks.add(task);
                    }
                }
            }
            if (tasks.size() > MAX_STEPS) {
                logger.warn("Planner returned {} tasks, keeping the first {}", tasks.size(), MAX_STEPS);
                tasks = new ArrayList<>(tasks.subList(0, MAX_STEPS));
            }
            if (!tasks.isEmpty()) {
                return tasks;
            }
        } catch (Exception e) {
            logger.error("Error in planning tasks, falling back to a single step", e);
        }
        return List.of(userQuery);
    }

    private LLMResponse executeStep(String history, String task, List<StepResult> done) {
        try {
            String routerQuery = combine(history, task);
            List<ToolCallback> tools = getToolsToPerformAction(routerQuery);

            if (tools.isEmpty()) {
                LLMResponse chat = aiService.getResponseFromAiAgent(routerQuery, Constants.Prompts.PRIMARY_AGENT_SYSTEM_INSTRUCTION);
                return chat.isToolCallingRequired() ? new LLMResponse(NO_TOOL_REPLY, true, true) : chat;
            }

            String executionQuery = combine(history, task + stepContext(done));
            return aiService.performToolCalling(executionQuery, tools);
        } catch (Exception e) {
            logger.error("Error in executing step [{}]", task, e);
            return LLMResponse.defaultResponse();
        }
    }

    private LLMResponse composeReply(String userQuery, List<String> tasks, List<StepResult> done) {
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
                    LLMResponse r = done.get(i).response();
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
        if (StringUtils.isEmpty(reply) || reply.isBlank()) {
            reply = done.stream().map(s -> s.response().result()).collect(Collectors.joining(" "));
        }
        return new LLMResponse(reply.strip(), true, anyError);
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

    private List<ToolCallback> getToolsToPerformAction(String query) {
        try {
            String catalog = toolProvider.getTools().stream()
                    .map(t -> t.key() + ": " + t.description())
                    .collect(Collectors.joining("\n"));
            String toolKeyString = aiService.executeLLMCall(query, Constants.Prompts.TOOL_IDENTIFIER.formatted(catalog), String.class);
            List<String> toolKeys = Arrays.stream(toolKeyString.split(","))
                    .map(String::strip)
                    .filter(k -> !k.isEmpty())
                    .distinct()
                    .toList();
            return toolProvider.getToolCallBack(toolKeys);
        } catch (Exception e) {
            logger.error("Error in identifying tool to perform action", e);
        }
        return List.of();
    }

    private void saveUserConversation(String sessionId, String userQuery, String filePath) {
        Conversation userConversation = Conversation.builder()
                .isLLMSuccess(false)
                .message(userQuery)
                .role(Role.USER)
                .sessionId(sessionId)
                .filePath(filePath)
                .build();
        conversationService.saveConversation(userConversation);
    }

    private String getHistoryBlock(String sessionId) {
        try {
            List<Conversation> previousConversations = getPreviousConversation(sessionId);
            return "Previous Conversion History:[\n"
                    + StringUtils.parseConversationListToString(previousConversations)
                    + "\n]";
        } catch (Exception e) {
            logger.error("Error in getting previous conversation from cache", e);
            return "";
        }
    }

    private String combine(String historyBlock, String currentMessage) {
        if (historyBlock == null || historyBlock.isEmpty()) {
            return currentMessage;
        }
        return historyBlock + "\n Current User message \n" + currentMessage;
    }

    private List<Conversation> getPreviousConversation(String sessionId) {
        List<Conversation> previousConversation = new ArrayList<>();
        try {
            previousConversation = cacheService.getSessionConversation(sessionId);
            if (previousConversation == null) {
                logger.info("Trying to fetch value from db");
                previousConversation = conversationService.getAllConversationOfTheSession(sessionId);
            }
            return previousConversation;
        } catch (Exception e) {
            logger.error("Error in fetching the previous conversation", e);
        }
        return previousConversation;
    }

    private String getSessionSummary(String sessionId) {
        String result = "Unable to save session";
        try {
            List<Conversation> conversationList = conversationService.getAllConversationOfTheSession(sessionId);
            if (conversationList != null) {
                String combinedConversationString = StringUtils.parseConversationListToString(conversationList);
                result = aiService.executeLLMCall(combinedConversationString, Constants.Prompts.SESSION_SUMMARY_INSTRUCTION, String.class);
            }
        } catch (Exception e) {
            logger.error("Error in generating summary for the session with session Id {}", sessionId, e);
        }
        return result;
    }

    private void stopWaitingThread(Thread waitingThread) {
        try {
            if (waitingThread != null) {
                if (waitingThread.isAlive()) {
                    waitingThread.interrupt();
                }
                synthesizer.stopSpeaking();
            }
        } catch (Exception e) {
            logger.info("Error in stopping waiting thread {}", e.getMessage());
        }
    }
}
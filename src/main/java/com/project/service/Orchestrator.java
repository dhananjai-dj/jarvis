package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.dao.entities.Role;
import com.project.dto.LLMResponse;
import com.project.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
public class Orchestrator {
    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);
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

            Conversation userConversation = Conversation.builder().isLLMSuccess(false).message(userQuery).role(Role.USER).sessionId(sessionId).filePath(filePath + Constants.INPUT_RECORDING_FILE_NAME).build();
            conversationService.saveConversation(userConversation);

            String combinedConversation = getCombinedConversation(sessionId, userQuery);
            logger.info("Complete transcript {}", combinedConversation);

            LLMResponse llmOutput = processUserQuery(sessionId, combinedConversation);
            String botReply = llmOutput.result();
            logger.info("Output from ml: {}", llmOutput);

            botReply = PiiMasker.unMaskValue(botReply);

            stopWaitingThread(waitingThread);

            if (!llmOutput.isToolCallingRequired() || llmOutput.isError()) {
                File outputFile = synthesizer.synthesize(botReply, filePath);
                synthesizer.speak(outputFile);
            }
        } catch (Exception e) {
            stopWaitingThread(waitingThread);
            synthesizer.speak(errorMessageFile);
            logger.error("Error in responding {}", e.getMessage());
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
            logger.error("Error in saving the summary {}", e.getMessage());
        }
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

            logger.info("Error in stoping waiting thread {}", e.getMessage());
        }
    }

    private String getCombinedConversation(String sessionId, String query) {
        try {
            StringJoiner stringJoiner = new StringJoiner("\n");
            stringJoiner.add("Previous Conversion History:[");
            List<Conversation> previousConversations = getPreviousConversation(sessionId);
            stringJoiner.add(StringUtils.parseConversationListToString(previousConversations));
            stringJoiner.add("]\n Current User message \n" + query);
            return stringJoiner.toString();
        } catch (Exception e) {
            logger.error("Error in getting previous conversation from cache {}", e.getMessage());
        }
        return query;
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
            logger.error("Error in fetching the previous conversation");
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
            logger.error("Error in generating summary for the session with session Id {}", sessionId);
        }
        return result;
    }


    private LLMResponse processUserQuery(String sessionId, String query) {
        LLMResponse llmResponse = null;
        try {
            llmResponse = aiService.getResponseFromAiAgent(query, Constants.Prompts.PRIMARY_AGENT_SYSTEM_INSTRUCTION);
            if (StringUtils.isEmpty(llmResponse.nextPrompt())) {
                conversationService.saveConversation(sessionId, llmResponse);
                return llmResponse;
            }
            return processUserQuery(sessionId, query, llmResponse.nextPrompt(), llmResponse.isToolCallingRequired());
        } catch (Exception e) {
            logger.error("Error in processing user query {}", e.getMessage());
        }
        return llmResponse;
    }

    private LLMResponse processUserQuery(String sessionId, String query, String prompt, boolean isToolCallingRequired) {
        LLMResponse llmResponse = null;
        try {
            if (isToolCallingRequired) {
                List<ToolCallback> tools = getToolsToPerformAction(query);
                llmResponse = aiService.performToolCalling(prompt, tools);
                conversationService.saveConversation(sessionId, llmResponse);
                return llmResponse;
            } else {
                llmResponse = aiService.getResponseFromAiAgent(query, prompt);
            }
            conversationService.saveConversation(sessionId, llmResponse);
            if (llmResponse.isToolCallingRequired() || !StringUtils.isEmpty(llmResponse.nextPrompt())) {
                return processUserQuery(sessionId, query, llmResponse.nextPrompt(), llmResponse.isToolCallingRequired());
            }
        } catch (Exception e) {
            logger.error("Error in Processing User Query {}", e.getMessage());
        }
        return llmResponse;
    }

    private List<ToolCallback> getToolsToPerformAction(String query) {
        try {
            String catalog = toolProvider.getTools().stream()
                    .map(t -> t.key() + ": " + t.description())
                    .collect(Collectors.joining("\n"));
            String toolKeyString = aiService.executeLLMCall(query, Constants.Prompts.TOOL_IDENTIFIER.formatted(catalog), String.class);
            List<String> toolKeys = Arrays.stream(toolKeyString.split(",")).map(String::strip).toList();
            return toolProvider.getToolCallBack(toolKeys);
        } catch (Exception e) {
            logger.error("Error in identifying tool to perform action {}", e.getMessage());
        }
        return List.of();
    }

    public void processText(String userQuery) throws Exception{
        Conversation userConversation = Conversation.builder().isLLMSuccess(false).message(userQuery).role(Role.USER).sessionId("TextSession").filePath("filePath" + Constants.INPUT_RECORDING_FILE_NAME).build();
        conversationService.saveConversation(userConversation);

        String combinedConversation = getCombinedConversation("TextSession", userQuery);

        LLMResponse llmOutput = processUserQuery("TextSession", combinedConversation);
        String botReply = llmOutput.result();
        logger.info("Output from ml: {}", llmOutput);

        botReply = PiiMasker.unMaskValue(botReply);
        if (!llmOutput.isToolCallingRequired() || llmOutput.isError()) {
            File outputFile = synthesizer.synthesize(botReply, "filePath");
            synthesizer.speak(outputFile);
        }
    }
}
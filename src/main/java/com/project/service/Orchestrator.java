package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.dao.entities.Role;
import com.project.dto.*;
import com.project.util.Constants;
import com.project.util.PiiMasker;
import com.project.util.Transcriber;
import com.project.util.StringUtils;
import com.project.util.Synthesizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class Orchestrator {
    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);


    private final File errorMessageFile = new File("error_message.wav");

    private final AiService aiService;
    private final Transcriber transcriber;
    private final Synthesizer synthesizer;
    private final CacheService cacheService;
    private final SessionService sessionService;
    private final RecordingService recordingService;
    private final ConversationService conversationService;
    private final QueryExecutionService queryExecutionService;


    public Orchestrator(Transcriber transcriber, AiService aiService, Synthesizer synthesizer, QueryExecutionService queryExecutionService, CacheService cacheService, SessionService sessionService, ConversationService conversationService, RecordingService recordingService) {
        this.aiService = aiService;
        this.transcriber = transcriber;
        this.synthesizer = synthesizer;
        this.cacheService = cacheService;
        this.sessionService = sessionService;
        this.recordingService = recordingService;
        this.conversationService = conversationService;
        this.queryExecutionService = queryExecutionService;

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
            String userQuery = transcriber.transcribes(wav);
            if (StringUtils.isEmpty(userQuery)) {
                throw new RuntimeException("Empty transcriber response !!!");
            }
            userQuery = PiiMasker.maskValue(userQuery);
            if (isNewSession) {
                sessionService.createSession(sessionId, userQuery);
            }

            saveUserConversation(sessionId, userQuery, filePath + Constants.INPUT_RECORDING_FILE_NAME);

            JarvisResponse llmOutput = handleUserQuery(sessionId, userQuery);
            logger.info("Output from ml: {}", llmOutput);

            String botReply = PiiMasker.unMaskValue(llmOutput.result());

            stopWaitingThread(waitingThread);

            File outputFile = synthesizer.synthesizes(botReply, filePath);
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

    private JarvisResponse handleUserQuery(String sessionId, String userQuery) {
        String history = getHistoryBlock(sessionId);
        String combined = combine(history, userQuery);
        JarvisResponse result = queryExecutionService.processUserQuery(combined, userQuery, history);
        conversationService.saveConversation(sessionId, result);
        return result;
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
package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.dao.entities.Role;
import com.project.dto.LLMResponse;
import com.project.util.Constants;
import com.project.util.ParserUtil;
import io.github.resilience4j.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@Service
public class Orchestrator {
    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);
    private final File errorMessageFile = new File("error_message.wav");


    private final AiService aiService;
    private final Transcriber transcriber;
    private final Synthesizer synthesizer;
    private final CacheService cacheService;
    private final SessionService sessionService;
    private final SpotifyToolFilter spotifyToolFilter;
    private final ConversationService conversationService;
    private final ToolCallbackProvider toolCallbackProvider;
    private final AudioRecorderService audioRecorderService;


    public Orchestrator(Transcriber transcriber, AiService aiService, Synthesizer synthesizer, CacheService cacheService, SessionService sessionService, SpotifyToolFilter spotifyToolFilter, ConversationService conversationService, ToolCallbackProvider toolCallbackProvider, AudioRecorderService audioRecorderService) {
        this.aiService = aiService;
        this.transcriber = transcriber;
        this.synthesizer = synthesizer;
        this.cacheService = cacheService;
        this.sessionService = sessionService;
        this.spotifyToolFilter = spotifyToolFilter;
        this.conversationService = conversationService;
        this.toolCallbackProvider = toolCallbackProvider;
        this.audioRecorderService = audioRecorderService;
    }

    public void respond(File wav, String sessionId, String filePath, boolean isNewSession) {
        synthesizer.resetStop();
        Thread waitingThread = new Thread(synthesizer::playWaitingMessage);
        waitingThread.start();
        try {
            String userQuery = transcriber.transcribe(wav);
            if (!StringUtils.isNotEmpty(userQuery)) {
                throw new RuntimeException("Empty transcriber response !!!");
            }
            if (isNewSession) {
                sessionService.createSession(sessionId, userQuery);
            }

            Conversation userConversation = Conversation.builder().isLLMSuccess(false).message(userQuery).role(Role.USER).sessionId(sessionId).filePath(filePath + Constants.INPUT_RECORDING_FILE_NAME).build();
            conversationService.saveConversation(userConversation);

            String combinedConversation = getCombinedConversation(sessionId, userQuery);
            logger.info("Complete transcript {}", combinedConversation);

            LLMResponse llmOutput = processUserQuery(combinedConversation);
            String botReply = llmOutput.result();
            logger.info("Output from ml: {}", llmOutput);

            Conversation botConversation = Conversation.builder().isLLMSuccess(llmOutput.isError()).message(botReply).role(Role.BOT).sessionId(sessionId).filePath(filePath + Constants.OUTPUT_RECORDING_FILE_NAME).build();
            conversationService.saveConversation(botConversation);

            stopWaitingThread(waitingThread);

            File outputFile = synthesizer.synthesize(botReply, filePath);
            synthesizer.speak(outputFile);
        } catch (Exception e) {
            stopWaitingThread(waitingThread);
            synthesizer.speak(errorMessageFile);
            logger.error("Error in responding {}", e.getMessage());
        }
    }

    public void startRecording(String sessionId, int conversationCount) {
        logger.info("Recording started for the session {} with conversation of count {}", sessionId, conversationCount);
        audioRecorderService.startRecording(Constants.RECORDING_PATH_PREFIX + sessionId + "/" + conversationCount);
    }

    public File stopRecording(String sessionId, int conversationCount) {
        logger.info("Recording stopped for the session {} with conversation of count {}", sessionId, conversationCount);
        return audioRecorderService.stopRecording();
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

    /* Helper Methods */
    private void stopWaitingThread(Thread waitingThread) {
        try {
            synthesizer.stopSpeaking();
            waitingThread.join();
        } catch (Exception e) {

            logger.info("Error in stoping waiting thread {}", e.getMessage());
        }
    }

    private String getCombinedConversation(String sessionId, String query) {
        try {
            StringJoiner stringJoiner = new StringJoiner("\n");
            stringJoiner.add("Previous Conversion History:[");
            List<Conversation> previousConversations = getPreviousConversation(sessionId);
            stringJoiner.add(ParserUtil.parseConversationListToString(previousConversations));
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
                previousConversation = conversationService.getPreviousConversations(sessionId);
            }
            return previousConversation;
        } catch (Exception e) {
            logger.error("Error in fetching the previous conversation");
        }
        return previousConversation;
    }

    private LLMResponse processUserQuery(String finalQuery) {
        try {
            LLMResponse llmResponse = aiService.getResponseFromAiAgent(finalQuery, Constants.Prompts.PRIMARY_AGENT_SYSTEM_INSTRUCTION);
            if (llmResponse.isError() || !llmResponse.isToolCallingRequired()) {
                return llmResponse;
            }
            LLMResponse toolCallResponse = aiService.getResponseFromAiAgent(finalQuery, Constants.Prompts.SPOTIFY_TOOL_IDENTIFIER);
            if (toolCallResponse.isError() || !toolCallResponse.isToolCallingRequired()) {
                return toolCallResponse;
            }
            List<ToolCallback> tools = spotifyToolFilter.getPlayRequestTools(toolCallbackProvider, toolCallResponse);
            if (!tools.isEmpty()) {
                String toolCallingResponse = aiService.performToolCalling(finalQuery, tools);
                logger.info("Tool calling response {}", toolCallingResponse);
                return new LLMResponse(toolCallingResponse, false, false);
            }
        } catch (Exception e) {
            logger.error("Error in processing User query {}", e.getMessage());
        }
        return LLMResponse.defaultResponse();
    }

    private String getSessionSummary(String sessionId) {
        String result = "Unable to save session";
        try {
            List<Conversation> conversationList = conversationService.getAllConversationOfTheSession(sessionId);
            if (conversationList != null) {
                String combinedConversationString = ParserUtil.parseConversationListToString(conversationList);
                result = aiService.getResponseFromSecondaryAiAgent(combinedConversationString, Constants.Prompts.SUMMARY_INSTRUCTION, null).result();
            }
        } catch (Exception e) {
            logger.error("Error in generating summary for the session with session Id {}", sessionId);
        }
        return result;
    }
}

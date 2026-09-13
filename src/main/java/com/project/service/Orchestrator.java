package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.dao.entities.Role;
import com.project.dto.LLMResponse;
import com.project.util.Constants;
import com.project.util.ParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@Service
public class Orchestrator {
    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);

    private final AiService aiService;
    private final Transcriber transcriber;
    private final Synthesizer synthesizer;
    private final CacheService cacheService;
    private final SessionService sessionService;
    private final ConversationService conversationService;


    public Orchestrator(Transcriber transcriber, AiService aiService, Synthesizer synthesizer, CacheService cacheService, SessionService sessionService, ConversationService conversationService) {
        this.aiService = aiService;
        this.transcriber = transcriber;
        this.synthesizer = synthesizer;
        this.cacheService = cacheService;
        this.sessionService = sessionService;
        this.conversationService = conversationService;
    }

    public void respond(File wav, String sessionId, String filePath, boolean isNewSession) {
        try {
            synthesizer.resetStop();
            Thread waitingThread = new Thread(synthesizer::playWaitingMessage);
            waitingThread.start();

            String userQuery = transcriber.transcribe(wav);
            logger.info("Transcript: {}", userQuery);
            if (isNewSession) {
                sessionService.createSession(sessionId, userQuery);
            }

            Conversation userConversation = Conversation.builder().isLLMSuccess(false).message(userQuery).role(Role.USER).sessionId(sessionId).filePath(filePath + Constants.INPUT_RECORDING_FILE_NAME).build();
            conversationService.saveConversation(userConversation);

            String combinedConversation = finalUserQuery(sessionId, userQuery);
            LLMResponse llmOutput = processUserQuery(combinedConversation);
            logger.info("Output from ml: {}", llmOutput);
            String botReply = llmOutput.result();

            Conversation botConversation = Conversation.builder().isLLMSuccess(llmOutput.isError()).message(botReply).role(Role.BOT).sessionId(sessionId).filePath(filePath + Constants.OUTPUT_RECORDING_FILE_NAME).build();
            conversationService.saveConversation(botConversation);
            synthesizer.stopSpeaking();

            cacheService.putToCache(sessionId, List.of(userConversation, botConversation));
            waitingThread.join();

            File outputFile = synthesizer.synthesize(botReply, filePath);
            synthesizer.speak(outputFile);
        } catch (Exception e) {
            logger.error("Error in responding {}", e.getMessage());
        }
    }

    public void saveSummary(String sessionId) {
        try {
            String summary = getSessionSummary(sessionId);
            sessionService.saveSessionSummary(sessionId, summary);
        } catch (Exception e) {
            logger.error("Error in saving the summary {}", e.getMessage());
        }
    }

    public void stopSpeech() {
        synthesizer.stopSpeaking();
    }

    private String finalUserQuery(String sessionId, String query) {
        try {
            StringJoiner stringJoiner = new StringJoiner("\n");
            stringJoiner.add("Previous Conversion History:[");
            List<Conversation> previousConversations = getPreviousConversation(sessionId);
            stringJoiner.add(ParserUtil.parseConversationListToString(previousConversations));
            stringJoiner.add("]\n Current User message \n" + query);
            logger.info("Combined query fetched successfully {}", stringJoiner.toString());
            return stringJoiner.toString();
        } catch (Exception e) {
            logger.error("Error in getting previous conversation from cache {}", e.getMessage());
        }
        return query;
    }

    private LLMResponse processUserQuery(String finalQuery) {
        try {
            return aiService.getResponseFromAiAgent(finalQuery);
        } catch (Exception e) {
            logger.error("Error in processing User query {}", e.getMessage());
        }
        return LLMResponse.defaultResponse();
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

    private String getSessionSummary(String sessionId) {
        String result = "Unable to save session";
        try {
            List<Conversation> conversationList = conversationService.getAllConversationOfTheSession(sessionId);
            if (conversationList != null) {
                String combinedConversationString = ParserUtil.parseConversationListToString(conversationList);
                result = aiService.getResponseFromLocalAiAgent(combinedConversationString, Constants.Prompts.SUMMARY_INSTRUCTION);
            }
        } catch (Exception e) {
            logger.error("Error in generating summary for the session with session Id {}", sessionId);
        }
        return result;
    }


}

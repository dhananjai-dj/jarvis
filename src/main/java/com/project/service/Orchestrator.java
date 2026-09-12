package com.project.service;

import com.project.dao.entities.Conversation;
import com.project.dto.LLMResponse;
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

    private final LLMService llmService;
    private final Transcriber transcriber;
    private final Synthesizer synthesizer;
    private final CacheService cacheService;
    private final SessionService sessionService;
    private final ConversationService conversationService;


    public Orchestrator(Transcriber transcriber, LLMService llmService, Synthesizer synthesizer, CacheService cacheService, SessionService sessionService, ConversationService conversationService) {
        this.llmService = llmService;
        this.transcriber = transcriber;
        this.synthesizer = synthesizer;
        this.cacheService = cacheService;
        this.sessionService = sessionService;
        this.conversationService = conversationService;
    }

    public void respond(File wav, String sessionId, boolean isNewSession) {
        try {
            synthesizer.resetStop();
            Thread waitingThread = new Thread(synthesizer::playWaitingMessage);
            waitingThread.start();

            String userQuery = transcriber.transcribe(wav);
            logger.info("Transcript: {}", userQuery);
            if (isNewSession) {
                sessionService.createSession(sessionId, userQuery);
            }

            Conversation userConversation = new Conversation();
            userConversation.setRole("User");
            userConversation.setMessage(userQuery);
            userConversation.setSessionId(sessionId);
            conversationService.saveConversation(userConversation);

            String combinedConversation = finalUserQuery(sessionId, userQuery);
            LLMResponse llmOutput = llmService.processUserQuery(combinedConversation);
            logger.info("Output from ml: {}", llmOutput);
            String botReply = llmOutput.result();

            Conversation botConversation = new Conversation();
            botConversation.setRole("Bot");
            botConversation.setSessionId(sessionId);
            botConversation.setMessage(botReply);
            botConversation.setLLMSuccess(llmOutput.isError());

            conversationService.saveConversation(botConversation);
            synthesizer.stopSpeaking();

            cacheService.putToCache(sessionId, List.of(userConversation, botConversation));
            waitingThread.join();

            logger.info("Responding to the question now");
            File outputFile = synthesizer.synthesize(botReply);
            synthesizer.speak(outputFile);
        } catch (Exception e) {
            logger.error("Error in responding {}", e.getMessage());
        }
    }

    public void saveSummary(String sessionId) {
        try {
            String summary = llmService.getSessionSummary(sessionId);
            sessionService.saveSessionSummary(sessionId, summary);
        } catch (Exception e) {
            logger.error("Error in saving the summary {}", e.getMessage());
        }
    }

    public void stopSpeech() {
        synthesizer.stopSpeaking();
    }

    public List<Conversation> getPreviousConversation(String sessionId) {
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

    private String finalUserQuery(String sessionId, String query) {
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


}

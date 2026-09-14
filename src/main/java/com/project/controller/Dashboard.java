package com.project.controller;

import com.project.dao.entities.Conversation;
import com.project.dao.entities.Session;
import com.project.service.ConversationService;
import com.project.service.SessionService;
import io.github.resilience4j.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/analytics")
public class Dashboard {

    private static final Logger logger = LoggerFactory.getLogger(Dashboard.class);

    private final ConversationService conversationService;
    private final SessionService sessionService;

    public Dashboard(ConversationService conversationService, SessionService sessionService) {
        this.conversationService = conversationService;
        this.sessionService = sessionService;
    }


    @GetMapping("conversation")
    public ResponseEntity<?> getAllConversation(@RequestParam(required = false) String sessionId) {
        try {
            List<Conversation> conversationList = null;
            logger.info(sessionId);
            if (StringUtils.isNotEmpty(sessionId)) {
                conversationList = conversationService.getAllConversationOfTheSession(sessionId);
            } else {
                conversationList = conversationService.getAllConversation();
            }
            return new ResponseEntity<>(conversationList, HttpStatus.FOUND);
        } catch (Exception e) {
            logger.error("Error in fetching conversation {}", e.getMessage());
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @GetMapping("session")
    public ResponseEntity<?> getSession(@RequestParam(required = false) String sessionId) {
        try {
            logger.info("session is {}", sessionId);
            List<Session> sessionList = null;
            if (StringUtils.isNotEmpty(sessionId)) {
                sessionList = List.of(sessionService.getSessionBySessionId(sessionId));
            } else {
                logger.info("Fetching all session");
                sessionList = sessionService.getAllSession();
            }
            return new ResponseEntity<>(sessionList, HttpStatus.FOUND);
        } catch (Exception e) {
            logger.error("Error in fetching session list {}", e.getMessage());
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
}

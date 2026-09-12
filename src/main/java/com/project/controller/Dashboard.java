package com.project.controller;

import com.project.dao.entities.Conversation;
import com.project.dao.entities.Session;
import com.project.service.ConversationService;
import com.project.service.SessionService;
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
    public ResponseEntity<?> getAllConversation(@RequestParam String sessionId) {
        try {
            List<Conversation> conversationList = null;
            if (sessionId != null) {
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
    public ResponseEntity<?> getSession(@RequestParam String sessionId) {
        try {
            List<Session> sessionList = null;
            if (sessionId != null) {
                sessionList = List.of(sessionService.getSessionBySessionId(sessionId));
            } else {
                sessionList = sessionService.getAllSession();
            }
            return new ResponseEntity<>(sessionList, HttpStatus.FOUND);
        } catch (Exception e) {
            logger.error("Error in fetching session list {}", e.getMessage());
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
}

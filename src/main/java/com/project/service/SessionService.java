package com.project.service;

import com.project.dao.entities.Session;
import com.project.dao.repositories.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Service
public class SessionService {
    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);
    private final UserSessionRepository userSessionRepository;

    public SessionService(UserSessionRepository userSessionRepository) {
        this.userSessionRepository = userSessionRepository;
    }

    public void createSession(String sessionId, String firstMessage) {
        try {
            Session session = new Session();
            session.setId(sessionId);
            session.setFirstMessage(firstMessage);
            userSessionRepository.save(session);
            logger.info("New Session created with session Id {}", sessionId);
        } catch (RuntimeException e) {
            logger.error("Error in creating session {}", e.getMessage());
        }
    }

    public void saveSessionSummary(String sessionId, String summary) {
        try {
            Session session = getSessionBySessionId(sessionId);
            if (session == null) {
                return;
            }
            session.setSummary(summary);
            session.setEndTime(Timestamp.from(Instant.now()));
            userSessionRepository.save(session);
            logger.info("Session with session Id {} updated", sessionId);
        } catch (Exception e) {
            logger.error("Error in saving the session summary {}", e.getMessage());
        }
    }

    public Session getSessionBySessionId(String sessionId) {
        try {
            return userSessionRepository.findById(sessionId).orElse(null);
        } catch (Exception e) {
            logger.error("Error in fetching session by session Id {}", e.getMessage());
        }
        return null;
    }

}

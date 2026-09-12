package com.project.dao.entities;


import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Entity
@Table(name = "conversation", indexes = {
        @Index(name = "session_index", columnList = "sessionId")
})
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    String sessionId;
    private String role;
    private String message;
    private boolean isLLMSuccess;

    public Timestamp getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Timestamp creationTime) {
        this.creationTime = creationTime;
    }

    @CreationTimestamp
    private Timestamp creationTime;

    @Override
    public String toString() {
        return "Conversation{" +
                "id=" + id +
                ", sessionId='" + sessionId + '\'' +
                ", role='" + role + '\'' +
                ", message='" + message + '\'' +
                ", isLLMSuccess=" + isLLMSuccess +
                '}';
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isLLMSuccess() {
        return isLLMSuccess;
    }

    public void setLLMSuccess(boolean LLMSuccess) {
        isLLMSuccess = LLMSuccess;
    }
}

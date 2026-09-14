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
    private String sessionId;
    private Role role;
    @Column(columnDefinition = "TEXT")
    private String message;
    private String filePath;
    private boolean isLLMSuccess;
    @CreationTimestamp
    private Timestamp creationTime;

    public Conversation() {
    }

    private Conversation(Builder builder) {
        this.role = builder.role;
        this.message = builder.message;
        this.filePath = builder.filePath;
        this.sessionId = builder.sessionId;
        this.isLLMSuccess = builder.isLLMSuccess;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Role role;
        private String message;
        private String filePath;
        private String sessionId;
        private boolean isLLMSuccess;


        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder isLLMSuccess(boolean isLLMSuccess) {
            this.isLLMSuccess = isLLMSuccess;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Conversation build() {
            return new Conversation(this);
        }
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
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

    public Timestamp getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Timestamp creationTime) {
        this.creationTime = creationTime;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public String toString() {
        return "Conversation{" +
                "id=" + id +
                ", sessionId='" + sessionId + '\'' +
                ", role='" + role + '\'' +
                ", message='" + message + '\'' +
                ", isLLMSuccess=" + isLLMSuccess +
                ", creationTime=" + creationTime +
                '}';
    }
}
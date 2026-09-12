package com.project.dao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Entity
public class Session {
    @Id
    private String id;
    private String firstMessage;

    @Column(columnDefinition = "TEXT")
    public String getFirstMessage() {
        return firstMessage;
    }

    @Column(columnDefinition = "TEXT")
    private String summary;

    @CreationTimestamp
    private Timestamp creationTime;

    public void setFirstMessage(String firstMessage) {
        this.firstMessage = firstMessage;
    }
    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Timestamp getEndTime() {
        return endTime;
    }

    public void setEndTime(Timestamp endTime) {
        this.endTime = endTime;
    }

    private Timestamp endTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Timestamp getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Timestamp creationTime) {
        this.creationTime = creationTime;
    }
}

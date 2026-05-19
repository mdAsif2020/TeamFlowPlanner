package com.teamflow.planner.data.entity;

public class ChatMessage {
    public String senderName;
    public String senderEmail;
    public String message;
    public long timestamp;

    public ChatMessage() {
        // Required for serialization
    }

    public ChatMessage(String senderName, String senderEmail, String message) {
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
}

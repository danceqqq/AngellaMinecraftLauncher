package com.angella.llm;

public class ChatMessage {
    private final String role;
    private final String content;
    private final long timestamp;

    public ChatMessage(String role, String content) {
        this(role, content, System.currentTimeMillis());
    }

    public ChatMessage(String role, String content, long timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }
}




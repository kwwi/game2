package com.example.boardgame.room;

import java.time.Instant;

public class ChatMessage {
    private final String id;
    private final String roomId;
    private final String userId;
    private final String name;
    private final String content;
    private final Instant ts;

    public ChatMessage(String id, String roomId, String userId, String name, String content, Instant ts) {
        this.id = id;
        this.roomId = roomId;
        this.userId = userId;
        this.name = name;
        this.content = content;
        this.ts = ts;
    }

    public String getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public Instant getTs() {
        return ts;
    }
}


package com.example.boardgame.room;

import com.example.boardgame.model.PieceColor;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class Room {
    private final String roomId;
    private final String name;

    private final Map<String, Participant> participantsById = new LinkedHashMap<>();
    private String blackPlayerUserId;
    private String whitePlayerUserId;

    private final Deque<ChatMessage> chat = new ArrayDeque<>();
    private final Map<String, Invite> invitesById = new LinkedHashMap<>();

    private final GameInstance game = new GameInstance();
    private PieceColor winner;
    private boolean hasMoves;
    private Path currentRecordFile;
    private Instant recordStartedAt;

    public Room(String roomId, String name) {
        this.roomId = roomId;
        this.name = name;
        startNewRecord();
    }

    public String getRoomId() {
        return roomId;
    }

    public String getName() {
        return name;
    }

    public GameInstance getGame() {
        return game;
    }

    public PieceColor getWinner() {
        return winner;
    }

    public void setWinner(PieceColor winner) {
        this.winner = winner;
    }

    public boolean hasMoves() {
        return hasMoves;
    }

    public void markMovePlayed() {
        this.hasMoves = true;
    }

    public Map<String, Participant> getParticipantsById() {
        return participantsById;
    }

    public String getBlackPlayerUserId() {
        return blackPlayerUserId;
    }

    public void setBlackPlayerUserId(String blackPlayerUserId) {
        this.blackPlayerUserId = blackPlayerUserId;
    }

    public String getWhitePlayerUserId() {
        return whitePlayerUserId;
    }

    public void setWhitePlayerUserId(String whitePlayerUserId) {
        this.whitePlayerUserId = whitePlayerUserId;
    }

    public boolean canJoinAsPlayer() {
        return blackPlayerUserId == null || whitePlayerUserId == null;
    }

    public List<ChatMessage> getChatHistory() {
        return new ArrayList<>(chat);
    }

    public Map<String, Invite> getInvitesById() {
        return invitesById;
    }

    public void addChat(ChatMessage msg, int max) {
        chat.addLast(msg);
        while (chat.size() > max) {
            chat.removeFirst();
        }
    }

    public Path getCurrentRecordFile() {
        return currentRecordFile;
    }

    public void setCurrentRecordFile(Path currentRecordFile) {
        this.currentRecordFile = currentRecordFile;
    }

    public Instant getRecordStartedAt() {
        return recordStartedAt;
    }

    public void setRecordStartedAt(Instant recordStartedAt) {
        this.recordStartedAt = recordStartedAt;
    }

    public void startNewRecord() {
        this.currentRecordFile = null;
        this.recordStartedAt = Instant.now();
    }
}


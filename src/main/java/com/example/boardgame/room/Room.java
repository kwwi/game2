package com.example.boardgame.room;

import com.example.boardgame.model.PieceColor;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class Room {
    private final String roomId;
    private final String name;
    // Per-room lock for correctness under concurrent requests.
    private final Object lock = new Object();

    private final Map<String, Participant> participantsById = new LinkedHashMap<>();
    private String blackPlayerUserId;
    private String whitePlayerUserId;

    private final Deque<ChatMessage> chat = new ArrayDeque<>();
    private final Map<String, Invite> invitesById = new LinkedHashMap<>();

    private final GameInstance game = new GameInstance();
    private PieceColor winner;
    private boolean hasMoves;
    // When not null, this user controls both BLACK and WHITE seats.
    private String selfPlayOwnerUserId;
    // When not null and winner == null, this user requested "restart another round".
    // The opponent must confirm (by clicking restart) for the round to actually restart.
    private String restartPendingFromUserId;
    // Used for frontend toast: who started the most recent round (restart).
    private String lastRoundStarterUserId;
    private Instant lastRoundStarterAt;
    private Path currentRecordFile;
    private Instant recordStartedAt;

    public Room(String roomId, String name) {
        this.roomId = roomId;
        this.name = name;
        startNewRecord();
    }

    public Object getLock() {
        return lock;
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

    public String getSelfPlayOwnerUserId() {
        return selfPlayOwnerUserId;
    }

    public void setSelfPlayOwnerUserId(String selfPlayOwnerUserId) {
        this.selfPlayOwnerUserId = selfPlayOwnerUserId;
    }

    public String getRestartPendingFromUserId() {
        return restartPendingFromUserId;
    }

    public void setRestartPendingFromUserId(String restartPendingFromUserId) {
        this.restartPendingFromUserId = restartPendingFromUserId;
    }

    public String getLastRoundStarterUserId() {
        return lastRoundStarterUserId;
    }

    public void setLastRoundStarterUserId(String lastRoundStarterUserId) {
        this.lastRoundStarterUserId = lastRoundStarterUserId;
    }

    public Instant getLastRoundStarterAt() {
        return lastRoundStarterAt;
    }

    public void setLastRoundStarterAt(Instant lastRoundStarterAt) {
        this.lastRoundStarterAt = lastRoundStarterAt;
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

    public void clearChat() {
        chat.clear();
    }

    public void clearInvites() {
        invitesById.clear();
    }

    public void resetGameToInitial() {
        game.reset(PieceColor.BLACK);
        winner = null;
        hasMoves = false;
        selfPlayOwnerUserId = null;
        restartPendingFromUserId = null;
        lastRoundStarterUserId = null;
        lastRoundStarterAt = null;
        blackPlayerUserId = null;
        whitePlayerUserId = null;
        clearChat();
        clearInvites();
        startNewRecord();
    }

    /**
     * Restart a new round inside the same room (keep participants / seats / self-play mode).
     */
    public void startNewRound(String starterUserId) {
        game.reset(PieceColor.BLACK);
        winner = null;
        hasMoves = false;
        restartPendingFromUserId = null;
        lastRoundStarterUserId = starterUserId;
        lastRoundStarterAt = Instant.now();
        startNewRecord();
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


package com.example.boardgame.dto;

import java.util.List;

public class RoomView {
    public String roomId;
    public String name;
    public String blackPlayerUserId;
    public String whitePlayerUserId;
    public String selfPlayOwnerUserId;
    public boolean selfPlayEnabled;
    public String restartPendingFromUserId;
    public String lastRoundStarterUserId;
    public String lastRoundStarterAt;
    public boolean canJoinAsPlayer;
    public List<ParticipantView> participants;
    public String recordFile;
    public String recordStartedAt;
}


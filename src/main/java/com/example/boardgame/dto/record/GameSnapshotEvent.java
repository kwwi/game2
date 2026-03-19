package com.example.boardgame.dto.record;

import java.util.Map;

public abstract class GameSnapshotEvent extends RecordEvent {
    public Map<String, Object> board;
    public String currentTurn;
    public String winner;
}


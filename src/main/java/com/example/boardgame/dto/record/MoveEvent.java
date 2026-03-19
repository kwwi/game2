package com.example.boardgame.dto.record;

public class MoveEvent extends GameSnapshotEvent {
    public String userId;
    public String seat;
    public int fromX;
    public int fromY;
    public int toX;
    public int toY;

    public MoveEvent() {
        this.type = "move";
    }
}


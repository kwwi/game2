package com.example.boardgame.dto.record;

public class EndEvent extends GameSnapshotEvent {
    public EndEvent() {
        this.type = "end";
    }
}


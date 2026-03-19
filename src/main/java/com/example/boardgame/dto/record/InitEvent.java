package com.example.boardgame.dto.record;

public class InitEvent extends GameSnapshotEvent {
    public InitEvent() {
        this.type = "init";
    }
}


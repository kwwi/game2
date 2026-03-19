package com.example.boardgame.dto;

import java.util.Map;

public class GameStateView {
    public RoomView room;
    public Map<String, Object> board;
    public String currentTurn;
    public String winner;
}


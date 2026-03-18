package com.example.boardgame.room;

import java.time.Instant;

public class Participant {
    private final String userId;
    private final String name;
    private RoomRole role;
    private Seat seat; // null if spectator
    private final Instant joinedAt;

    public Participant(String userId, String name, RoomRole role, Seat seat) {
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.seat = seat;
        this.joinedAt = Instant.now();
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public RoomRole getRole() {
        return role;
    }

    public void setRole(RoomRole role) {
        this.role = role;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}


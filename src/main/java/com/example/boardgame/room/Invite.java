package com.example.boardgame.room;

import java.time.Instant;

public class Invite {
    private final String inviteId;
    private final String roomId;
    private final String fromUserId;
    private final String toUserId;
    private final Seat seat;
    private InviteStatus status;
    private final Instant createdAt;
    private Instant resolvedAt;

    public Invite(String inviteId, String roomId, String fromUserId, String toUserId, Seat seat) {
        this.inviteId = inviteId;
        this.roomId = roomId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.seat = seat;
        this.status = InviteStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public String getInviteId() {
        return inviteId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public Seat getSeat() {
        return seat;
    }

    public InviteStatus getStatus() {
        return status;
    }

    public void setStatus(InviteStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}


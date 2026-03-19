package com.example.boardgame.dto;

public class InviteView {
    public String inviteId;
    public String roomId;
    public String fromUserId;
    public String toUserId;
    public String seat;   // BLACK / WHITE
    public String status; // PENDING / ACCEPTED / DECLINED / ...
    public String createdAt;
    public String resolvedAt;
}


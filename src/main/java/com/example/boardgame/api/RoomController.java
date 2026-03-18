package com.example.boardgame.api;

import com.example.boardgame.room.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import static com.example.boardgame.util.Maps.of;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin
public class RoomController {

    private final RoomService roomService;
    private final RoomEventHub roomEventHub;

    public RoomController(RoomService roomService, RoomEventHub roomEventHub) {
        this.roomService = roomService;
        this.roomEventHub = roomEventHub;
    }

    @GetMapping
    public List<Map<String, Object>> listRooms() {
        return roomService.listRooms();
    }

    @GetMapping("/{roomId}/events")
    public SseEmitter events(@PathVariable String roomId, @RequestParam String userId) {
        return roomEventHub.connect(roomId, userId);
    }

    public static class JoinRequest {
        public String userId;
        public String name;
        public String mode; // PLAYER / SPECTATOR
    }

    @PostMapping("/{roomId}/join")
    public Map<String, Object> join(@PathVariable String roomId, @RequestBody JoinRequest req) {
        RoomRole desired = "PLAYER".equalsIgnoreCase(req.mode) ? RoomRole.PLAYER : RoomRole.SPECTATOR;
        return roomService.join(roomId, req.userId, req.name, desired);
    }

    public static class LeaveRequest {
        public String userId;
    }

    @PostMapping("/{roomId}/leave")
    public Map<String, Object> leave(@PathVariable String roomId, @RequestBody LeaveRequest req) {
        roomService.leave(roomId, req.userId);
        return of("success", true);
    }

    @GetMapping("/{roomId}/state")
    public Map<String, Object> state(@PathVariable String roomId) {
        return roomService.getGameState(roomId);
    }

    public static class MoveRequest {
        public String userId;
        public int fromX;
        public int fromY;
        public int toX;
        public int toY;
    }

    @PostMapping("/{roomId}/move")
    public com.example.boardgame.room.GameInstance.MoveResult move(@PathVariable String roomId, @RequestBody MoveRequest req) {
        return roomService.move(roomId, req.userId, req.fromX, req.fromY, req.toX, req.toY);
    }

    public static class ChatRequest {
        public String userId;
        public String content;
    }

    @GetMapping("/{roomId}/chat")
    public List<ChatMessage> chatHistory(@PathVariable String roomId) {
        return roomService.chatHistory(roomId);
    }

    @PostMapping("/{roomId}/chat")
    public ChatMessage chat(@PathVariable String roomId, @RequestBody ChatRequest req) {
        return roomService.sendChat(roomId, req.userId, req.content);
    }

    public static class CreateInviteRequest {
        public String fromUserId;
        public String toUserId;
        public String seat; // BLACK / WHITE
    }

    @PostMapping("/{roomId}/invites")
    public Map<String, Object> createInvite(@PathVariable String roomId, @RequestBody CreateInviteRequest req) {
        Seat seat = "WHITE".equalsIgnoreCase(req.seat) ? Seat.WHITE : Seat.BLACK;
        return roomService.createInvite(roomId, req.fromUserId, req.toUserId, seat);
    }

    public static class RespondInviteRequest {
        public String userId;
        public String action; // ACCEPT / DECLINE
    }

    @PostMapping("/{roomId}/invites/{inviteId}/respond")
    public Map<String, Object> respondInvite(
            @PathVariable String roomId,
            @PathVariable String inviteId,
            @RequestBody RespondInviteRequest req
    ) {
        boolean accept = "ACCEPT".equalsIgnoreCase(req.action);
        return roomService.respondInvite(roomId, inviteId, req.userId, accept);
    }

    @GetMapping("/records")
    public List<Map<String, Object>> records() {
        return roomService.listRecords();
    }

    @GetMapping("/records/{recordId}")
    public Map<String, Object> record(@PathVariable String recordId) throws IOException {
        return of("id", recordId, "content", roomService.readRecord(recordId));
    }
}


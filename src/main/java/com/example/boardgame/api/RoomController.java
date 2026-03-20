package com.example.boardgame.api;

import com.example.boardgame.room.*;
import com.example.boardgame.dto.*;
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
    public List<RoomView> listRooms() {
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
    public JoinRoomResponse join(@PathVariable String roomId, @RequestBody JoinRequest req) {
        RoomRole desired = "PLAYER".equalsIgnoreCase(req.mode) ? RoomRole.PLAYER : RoomRole.SPECTATOR;
        return roomService.join(roomId, req.userId, req.name, desired);
    }

    public static class LeaveRequest {
        public String userId;
    }

    public static class SelfPlayRequest {
        public String userId;
        public boolean enabled;
    }

    public static class AdminResetRequest {
        public String userId;
        public String name;
    }

    @PostMapping("/{roomId}/admin-reset")
    public ApiResponse<RoomView> adminReset(@PathVariable String roomId, @RequestBody AdminResetRequest req) {
        return roomService.adminResetRoom(roomId, req.userId, req.name);
    }

    public static class RematchRequest {
        public String userId;
        public String name; // optional, server prefers participant name
    }

    @PostMapping("/{roomId}/rematch")
    public ApiResponse<RoomView> rematch(@PathVariable String roomId, @RequestBody RematchRequest req) {
        return roomService.proposeRematch(roomId, req.userId, req.name);
    }

    @PostMapping("/{roomId}/leave")
    public Map<String, Object> leave(@PathVariable String roomId, @RequestBody LeaveRequest req) {
        roomService.leave(roomId, req.userId);
        return of("success", true);
    }

    @PostMapping("/{roomId}/leave-game")
    public Map<String, Object> leaveGameAsSpectator(@PathVariable String roomId, @RequestBody LeaveRequest req) {
        roomService.leaveGameAsSpectator(roomId, req.userId);
        return of("success", true);
    }

    @PostMapping("/{roomId}/self-play")
    public ApiResponse<RoomView> setSelfPlayMode(@PathVariable String roomId, @RequestBody SelfPlayRequest req) {
        return roomService.setSelfPlayMode(roomId, req.userId, req.enabled);
    }

    public static class RestartRequest {
        public String userId;
    }

    @PostMapping("/{roomId}/restart")
    public ApiResponse<RoomView> restartRound(@PathVariable String roomId, @RequestBody RestartRequest req) {
        return roomService.restartRound(roomId, req.userId);
    }

    @PostMapping("/{roomId}/restart-cancel")
    public ApiResponse<RoomView> cancelRestartRound(@PathVariable String roomId, @RequestBody RestartRequest req) {
        return roomService.cancelRestartRound(roomId, req.userId);
    }

    @PostMapping("/{roomId}/seats/{seat}/join")
    public ApiResponse<RoomView> joinSeat(@PathVariable String roomId, @PathVariable String seat, @RequestBody LeaveRequest req) {
        Seat s = "WHITE".equalsIgnoreCase(seat) ? Seat.WHITE : Seat.BLACK;
        return roomService.joinSeat(roomId, req.userId, s);
    }

    @GetMapping("/{roomId}/state")
    public GameStateView state(@PathVariable String roomId) {
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
    public ApiResponse<InviteView> createInvite(@PathVariable String roomId, @RequestBody CreateInviteRequest req) {
        Seat seat = "WHITE".equalsIgnoreCase(req.seat) ? Seat.WHITE : Seat.BLACK;
        return roomService.createInvite(roomId, req.fromUserId, req.toUserId, seat);
    }

    public static class RespondInviteRequest {
        public String userId;
        public String action; // ACCEPT / DECLINE
    }

    @PostMapping("/{roomId}/invites/{inviteId}/respond")
    public ApiResponse<RoomView> respondInvite(
            @PathVariable String roomId,
            @PathVariable String inviteId,
            @RequestBody RespondInviteRequest req
    ) {
        boolean accept = "ACCEPT".equalsIgnoreCase(req.action);
        return roomService.respondInvite(roomId, inviteId, req.userId, accept);
    }

    @GetMapping("/records")
    public List<RecordInfo> records() {
        return roomService.listRecords();
    }

    @GetMapping("/records/{recordId}")
    public RecordContent record(@PathVariable String recordId) throws IOException {
        RecordContent rc = new RecordContent();
        rc.id = recordId;
        rc.content = roomService.readRecord(recordId);
        return rc;
    }
}


package com.example.boardgame.room;

import com.example.boardgame.model.PieceColor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.example.boardgame.util.Maps.of;

@Service
public class RoomService {

    private static final int ROOM_COUNT = 10;
    private static final int CHAT_MAX = 200;
    private static final DateTimeFormatter RECORD_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());

    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Path recordsDir;
    private final RoomEventHub eventHub;

    public RoomService(RoomEventHub eventHub) {
        this.eventHub = eventHub;
        this.recordsDir = Paths.get("records");
        try {
            Files.createDirectories(recordsDir);
        } catch (IOException ignore) {
        }
        for (int i = 1; i <= ROOM_COUNT; i++) {
            String id = String.valueOf(i);
            rooms.put(id, new Room(id, "房间 " + id));
            ensureRoomRecordFile(id);
            // record only game lifecycle: init snapshot
            Room r = rooms.get(id);
            if (r != null) {
                appendGameEvent(r, of(
                        "type", "init",
                        "ts", Instant.now().toString(),
                        "board", r.getGame().buildBoardView(),
                        "currentTurn", r.getGame().getCurrentTurn().toString(),
                        "winner", null
                ));
            }
        }
    }

    public synchronized List<Map<String, Object>> listRooms() {
        List<Map<String, Object>> res = new ArrayList<>();
        for (Room r : rooms.values()) {
            res.add(roomView(r));
        }
        return res;
    }

    public synchronized Map<String, Object> getRoom(String roomId) {
        Room r = requireRoom(roomId);
        return roomView(r);
    }

    public synchronized Map<String, Object> join(String roomId, String userId, String name, RoomRole desired) {
        Room r = requireRoom(roomId);
        Participant existing = r.getParticipantsById().get(userId);
        if (existing != null) {
            // Existing participant can upgrade/downgrade based on available seat.
            if (desired == RoomRole.PLAYER) {
                // Upgrade spectator -> player only when a seat is available.
                if (existing.getSeat() == null) {
                    if (r.getBlackPlayerUserId() == null) {
                        existing.setRole(RoomRole.PLAYER);
                        existing.setSeat(Seat.BLACK);
                        r.setBlackPlayerUserId(userId);
                    } else if (r.getWhitePlayerUserId() == null) {
                        existing.setRole(RoomRole.PLAYER);
                        existing.setSeat(Seat.WHITE);
                        r.setWhitePlayerUserId(userId);
                    } else {
                        existing.setRole(RoomRole.SPECTATOR);
                        existing.setSeat(null);
                    }
                }
            } else {
                // Downgrade to spectator and free seat if user currently holds one.
                if (existing.getSeat() != null) {
                    if (existing.getSeat() == Seat.BLACK && userId.equals(r.getBlackPlayerUserId())) {
                        r.setBlackPlayerUserId(null);
                    }
                    if (existing.getSeat() == Seat.WHITE && userId.equals(r.getWhitePlayerUserId())) {
                        r.setWhitePlayerUserId(null);
                    }
                }
                existing.setRole(RoomRole.SPECTATOR);
                existing.setSeat(null);
            }

            eventHub.broadcast(roomId, "room", of("type", "room", "room", roomView(r)));
            return roomJoinView(r, existing);
        }

        // Determine actual role
        RoomRole actualRole = desired;
        Seat seat = null;
        if (desired == RoomRole.PLAYER && r.canJoinAsPlayer()) {
            if (r.getBlackPlayerUserId() == null) {
                r.setBlackPlayerUserId(userId);
                seat = Seat.BLACK;
            } else if (r.getWhitePlayerUserId() == null) {
                r.setWhitePlayerUserId(userId);
                seat = Seat.WHITE;
            } else {
                actualRole = RoomRole.SPECTATOR;
            }
        } else {
            actualRole = RoomRole.SPECTATOR;
        }

        Participant p = new Participant(userId, name, actualRole, seat);
        r.getParticipantsById().put(userId, p);

        eventHub.broadcast(roomId, "room", of("type", "room", "room", roomView(r)));
        return roomJoinView(r, p);
    }

    public synchronized void leave(String roomId, String userId) {
        Room r = requireRoom(roomId);
        Participant p = r.getParticipantsById().remove(userId);
        if (p == null) return;

        Seat seat = userId.equals(r.getBlackPlayerUserId()) ? Seat.BLACK
                : userId.equals(r.getWhitePlayerUserId()) ? Seat.WHITE
                : null;

        if (userId.equals(r.getBlackPlayerUserId())) {
            r.setBlackPlayerUserId(null);
        }
        if (userId.equals(r.getWhitePlayerUserId())) {
            r.setWhitePlayerUserId(null);
        }

        // 完全退出房间：不判胜负。对局状态保持不变，等待其他用户补位后继续对弈。
        eventHub.broadcast(roomId, "room", of("type", "room", "room", roomView(r)));
    }

    /**
     * 退出对弈（释放席位但留在房间作为观众），不判胜负。
     * 用于“棋手先离开对局，但房间还在，允许其它新进入用户选择接手继续对弈”。
     */
    public synchronized void leaveGameAsSpectator(String roomId, String userId) {
        Room r = requireRoom(roomId);
        Participant p = r.getParticipantsById().get(userId);
        if (p == null) return;

        boolean wasPlayer = userId.equals(r.getBlackPlayerUserId()) || userId.equals(r.getWhitePlayerUserId());
        if (!wasPlayer) {
            return;
        }

        if (userId.equals(r.getBlackPlayerUserId())) {
            r.setBlackPlayerUserId(null);
        }
        if (userId.equals(r.getWhitePlayerUserId())) {
            r.setWhitePlayerUserId(null);
        }

        p.setRole(RoomRole.SPECTATOR);
        p.setSeat(null);

        eventHub.broadcast(roomId, "room", of("type", "room", "room", roomView(r)));
        eventHub.broadcast(roomId, "state", of(
                "type", "state",
                "board", r.getGame().buildBoardView(),
                "currentTurn", r.getGame().getCurrentTurn().toString(),
                "winner", r.getWinner(),
                "room", roomView(r)
        ));
    }

    public synchronized List<ChatMessage> chatHistory(String roomId) {
        Room r = requireRoom(roomId);
        return r.getChatHistory();
    }

    public synchronized ChatMessage sendChat(String roomId, String userId, String content) {
        Room r = requireRoom(roomId);
        Participant p = r.getParticipantsById().get(userId);
        String name = p != null ? p.getName() : "匿名";
        ChatMessage msg = new ChatMessage(UUID.randomUUID().toString(), roomId, userId, name, content, Instant.now());
        r.addChat(msg, CHAT_MAX);
        eventHub.broadcast(roomId, "chat", of("type", "chat", "message", of(
                "id", msg.getId(),
                "roomId", msg.getRoomId(),
                "userId", msg.getUserId(),
                "name", msg.getName(),
                "content", msg.getContent(),
                "ts", msg.getTs().toString()
        )));
        return msg;
    }

    public synchronized Map<String, Object> getGameState(String roomId) {
        Room r = requireRoom(roomId);
        Map<String, Object> res = new HashMap<>();
        res.put("room", roomView(r));
        res.put("board", r.getGame().buildBoardView());
        res.put("currentTurn", r.getGame().getCurrentTurn());
        res.put("winner", r.getWinner());
        return res;
    }

    public synchronized GameInstance.MoveResult move(String roomId, String userId, int fromX, int fromY, int toX, int toY) {
        Room r = requireRoom(roomId);
        if (r.getWinner() != null) {
            GameInstance.MoveResult denied = new GameInstance.MoveResult();
            denied.success = false;
            denied.message = "对局已结束";
            denied.board = r.getGame().buildBoardView();
            denied.currentTurn = r.getGame().getCurrentTurn();
            denied.winner = r.getWinner();
            return denied;
        }
        Participant p = r.getParticipantsById().get(userId);
        if (p == null || p.getRole() != RoomRole.PLAYER || p.getSeat() == null) {
            GameInstance.MoveResult denied = new GameInstance.MoveResult();
            denied.success = false;
            denied.message = "只有棋手可以下棋";
            denied.board = r.getGame().buildBoardView();
            denied.currentTurn = r.getGame().getCurrentTurn();
            denied.winner = null;
            return denied;
        }

        PieceColor mustColor = (p.getSeat() == Seat.BLACK) ? PieceColor.BLACK : PieceColor.WHITE;
        if (r.getGame().getCurrentTurn() != mustColor) {
            GameInstance.MoveResult denied = new GameInstance.MoveResult();
            denied.success = false;
            denied.message = "还没轮到你";
            denied.board = r.getGame().buildBoardView();
            denied.currentTurn = r.getGame().getCurrentTurn();
            denied.winner = null;
            return denied;
        }

        GameInstance.MoveResult res = r.getGame().move(fromX, fromY, toX, toY);
        if (res.success) {
            r.markMovePlayed();
            appendGameEvent(r, of(
                    "type", "move",
                    "ts", Instant.now().toString(),
                    "userId", userId,
                    "seat", p.getSeat().toString(),
                    "fromX", fromX,
                    "fromY", fromY,
                    "toX", toX,
                    "toY", toY,
                    "board", res.board,
                    "currentTurn", res.currentTurn == null ? null : res.currentTurn.toString(),
                    "winner", res.winner == null ? null : res.winner.toString()
            ));
            if (res.winner != null) {
                r.setWinner(res.winner);
                appendGameEvent(r, of(
                        "type", "end",
                        "ts", Instant.now().toString(),
                        "winner", res.winner.toString(),
                        "board", res.board,
                        "currentTurn", res.currentTurn == null ? null : res.currentTurn.toString()
                ));
            }
            eventHub.broadcast(roomId, "state", of(
                    "type", "state",
                    "board", res.board,
                    "currentTurn", res.currentTurn == null ? null : res.currentTurn.toString(),
                    "winner", res.winner == null ? null : res.winner.toString(),
                    "room", roomView(r)
            ));
        }
        return res;
    }

    public synchronized Map<String, Object> createInvite(String roomId, String fromUserId, String toUserId, Seat seat) {
        Room r = requireRoom(roomId);
        Participant from = r.getParticipantsById().get(fromUserId);
        Participant to = r.getParticipantsById().get(toUserId);
        if (from == null || to == null) {
            return of("success", false, "message", "用户不在房间内");
        }
        if (from.getRole() != RoomRole.PLAYER || from.getSeat() == null) {
            return of("success", false, "message", "只有棋手可以发起替换邀请");
        }
        if (seat == null) {
            return of("success", false, "message", "seat不能为空");
        }
        // Only allow replacing the requester's own seat
        if (from.getSeat() != seat) {
            return of("success", false, "message", "只能替换自己的席位");
        }

        // 正在对弈的双方不允许互相替换
        String otherUserId = (seat == Seat.BLACK) ? r.getWhitePlayerUserId() : r.getBlackPlayerUserId();
        if (otherUserId != null && otherUserId.equals(toUserId)) {
            return of("success", false, "message", "对弈双方不能互相替换");
        }

        String oldUserId = (seat == Seat.BLACK) ? r.getBlackPlayerUserId() : r.getWhitePlayerUserId();
        if (oldUserId == null || !oldUserId.equals(fromUserId)) {
            return of("success", false, "message", "席位信息不一致");
        }

        String inviteId = UUID.randomUUID().toString();
        Invite invite = new Invite(inviteId, roomId, fromUserId, toUserId, seat);
        r.getInvitesById().put(inviteId, invite);
        Map<String, Object> inviteView = of(
                "inviteId", invite.getInviteId(),
                "roomId", invite.getRoomId(),
                "fromUserId", invite.getFromUserId(),
                "toUserId", invite.getToUserId(),
                "seat", invite.getSeat().toString(),
                "status", invite.getStatus().toString(),
                "createdAt", invite.getCreatedAt().toString()
        );
        // push to receiver
        eventHub.sendToUser(roomId, toUserId, "invite", of("type", "invite", "invite", inviteView));
        // also notify room list update if desired
        eventHub.broadcast(roomId, "room", of("type", "room", "room", roomView(r)));

        return of("success", true, "message", "ok", "invite", inviteView);
    }

    public synchronized Map<String, Object> respondInvite(String roomId, String inviteId, String userId, boolean accept) {
        Room r = requireRoom(roomId);
        Invite invite = r.getInvitesById().get(inviteId);
        if (invite == null) return of("success", false, "message", "邀请不存在");
        if (!invite.getToUserId().equals(userId)) return of("success", false, "message", "无权限响应该邀请");
        if (invite.getStatus() != InviteStatus.PENDING) return of("success", false, "message", "邀请已处理");

        if (!accept) {
            invite.setStatus(InviteStatus.DECLINED);
            invite.setResolvedAt(Instant.now());
            eventHub.sendToUser(roomId, invite.getFromUserId(), "inviteResolved", of(
                    "type", "inviteResolved",
                    "inviteId", inviteId,
                    "status", "DECLINED"
            ));
            return of("success", true, "message", "已拒绝");
        }

        // accept -> perform replace if still valid
        Participant from = r.getParticipantsById().get(invite.getFromUserId());
        Participant to = r.getParticipantsById().get(invite.getToUserId());
        if (from == null || to == null) {
            invite.setStatus(InviteStatus.CANCELLED);
            invite.setResolvedAt(Instant.now());
            return of("success", false, "message", "棋手或被邀请者已不在房间");
        }
        if (from.getRole() != RoomRole.PLAYER || from.getSeat() == null || from.getSeat() != invite.getSeat()) {
            invite.setStatus(InviteStatus.CANCELLED);
            invite.setResolvedAt(Instant.now());
            return of("success", false, "message", "邀请已失效（席位变化）");
        }
        String oldUserId = (invite.getSeat() == Seat.BLACK) ? r.getBlackPlayerUserId() : r.getWhitePlayerUserId();
        if (oldUserId == null || !oldUserId.equals(invite.getFromUserId())) {
            invite.setStatus(InviteStatus.CANCELLED);
            invite.setResolvedAt(Instant.now());
            return of("success", false, "message", "邀请已失效（席位变化）");
        }

        // Replace: old player -> spectator; new -> player with seat
        if (invite.getSeat() == Seat.BLACK) {
            r.setBlackPlayerUserId(invite.getToUserId());
        } else {
            r.setWhitePlayerUserId(invite.getToUserId());
        }
        from.setRole(RoomRole.SPECTATOR);
        from.setSeat(null);
        to.setRole(RoomRole.PLAYER);
        to.setSeat(invite.getSeat());

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setResolvedAt(Instant.now());

        eventHub.broadcast(roomId, "room", of("type", "room", "room", roomView(r)));
        eventHub.broadcast(roomId, "state", of(
                "type", "state",
                "board", r.getGame().buildBoardView(),
                "currentTurn", r.getGame().getCurrentTurn().toString(),
                "winner", null,
                "room", roomView(r)
        ));
        eventHub.sendToUser(roomId, invite.getFromUserId(), "inviteResolved", of(
                "type", "inviteResolved",
                "inviteId", inviteId,
                "status", "ACCEPTED"
        ));

        return of("success", true, "message", "已同意并完成替换", "room", roomView(r));
    }

    public synchronized List<Map<String, Object>> listRecords() {
        try {
            if (!Files.exists(recordsDir)) return Collections.emptyList();
            List<Map<String, Object>> res = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(recordsDir, "*.jsonl")) {
                for (Path p : ds) {
                    res.add(of("id", p.getFileName().toString(), "path", p.toString()));
                }
            }
            res.sort(Comparator.comparing(m -> String.valueOf(m.get("id"))));
            return res;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Only return records that belong to the given user.
     * Heuristic: record JSONL lines include `"userId":"..."`
     */
    public synchronized List<Map<String, Object>> listMyRecords(String userId) {
        try {
            if (!Files.exists(recordsDir)) return Collections.emptyList();
            String needle = "\"userId\":\"" + escape(userId) + "\"";

            List<Map<String, Object>> res = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(recordsDir, "*.jsonl")) {
                for (Path p : ds) {
                    String content = Files.readString(p, StandardCharsets.UTF_8);
                    if (content.contains(needle)) {
                        res.add(of("id", p.getFileName().toString(), "path", p.toString()));
                    }
                }
            }
            res.sort(Comparator.comparing(m -> String.valueOf(m.get("id"))));
            return res;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public synchronized String readRecord(String recordId) throws IOException {
        Path p = recordsDir.resolve(recordId).normalize();
        if (!p.startsWith(recordsDir)) throw new IOException("invalid record id");
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    public synchronized Map<String, Object> loadRecordIntoRoom(String recordId, String roomId) throws IOException {
        Room r = requireRoom(roomId);
        String content = readRecord(recordId);
        String[] lines = content.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.contains("\"type\":\"move\"") && line.contains("\"board\"")) {
                // Very small JSON parser: rely on Jackson in controller; here we just break and let controller parse.
                // We will store the raw line for controller to parse, so return it.
                return of("success", true, "message", "ok", "rawEvent", line);
            }
        }
        // no moves: reset
        r.getGame().reset(PieceColor.BLACK);
        return of("success", true, "message", "no moves, reset", "state", getGameState(roomId));
    }

    public synchronized Map<String, Object> restoreSnapshot(String roomId, Map<String, Object> boardView, PieceColor currentTurn) {
        Room r = requireRoom(roomId);
        r.getGame().loadFromSnapshot(boardView, currentTurn);
        r.setWinner(null);
        return getGameState(roomId);
    }

    // -------- helpers --------

    private Room requireRoom(String roomId) {
        Room r = rooms.get(roomId);
        if (r == null) throw new IllegalArgumentException("room not found");
        ensureRoomRecordFile(roomId);
        return r;
    }

    private Map<String, Object> roomView(Room r) {
        Map<String, Object> res = new HashMap<>();
        res.put("roomId", r.getRoomId());
        res.put("name", r.getName());
        res.put("blackPlayerUserId", r.getBlackPlayerUserId());
        res.put("whitePlayerUserId", r.getWhitePlayerUserId());
        res.put("canJoinAsPlayer", r.canJoinAsPlayer());

        List<Map<String, Object>> users = new ArrayList<>();
        for (Participant p : r.getParticipantsById().values()) {
            users.add(of(
                    "userId", p.getUserId(),
                    "name", p.getName(),
                    "role", p.getRole().toString(),
                    "seat", p.getSeat() == null ? null : p.getSeat().toString(),
                    "joinedAt", p.getJoinedAt().toString()
            ));
        }
        res.put("participants", users);
        res.put("recordFile", r.getCurrentRecordFile() == null ? null : r.getCurrentRecordFile().getFileName().toString());
        res.put("recordStartedAt", r.getRecordStartedAt() == null ? null : r.getRecordStartedAt().toString());
        return res;
    }

    private Map<String, Object> roomJoinView(Room r, Participant me) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("me", of(
                "userId", me.getUserId(),
                "name", me.getName(),
                "role", me.getRole().toString(),
                "seat", me.getSeat() == null ? null : me.getSeat().toString()
        ));
        res.put("room", roomView(r));
        res.put("state", getGameState(r.getRoomId()));
        return res;
    }

    private void ensureRoomRecordFile(String roomId) {
        Room r = rooms.get(roomId);
        if (r == null) return;
        if (r.getCurrentRecordFile() != null && Files.exists(r.getCurrentRecordFile())) return;
        String file = "room_" + roomId + "_" + RECORD_TS.format(Instant.now()) + ".jsonl";
        Path p = recordsDir.resolve(file);
        try {
            Files.createFile(p);
        } catch (FileAlreadyExistsException ignore) {
        } catch (IOException ignore) {
        }
        r.setCurrentRecordFile(p);
        r.setRecordStartedAt(Instant.now());
    }

    private void appendGameEvent(Room r, Map<String, Object> event) {
        // Only record: init / move / end / quit
        Object t = event.get("type");
        if (t == null) return;
        String type = String.valueOf(t);
        if (!Set.of("init", "move", "end", "quit").contains(type)) return;
        appendRecordEvent(r, event);
    }

    private void appendRecordEvent(Room r, Map<String, Object> event) {
        ensureRoomRecordFile(r.getRoomId());
        Path p = r.getCurrentRecordFile();
        if (p == null) return;
        try {
            // simple JSON serialization without introducing new deps: use StringBuilder for a limited set
            String json = toJson(event);
            Files.writeString(p, json + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignore) {
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escape((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> m = (Map<Object, Object>) obj;
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(String.valueOf(e.getKey())));
                sb.append(":");
                sb.append(toJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<Object> c = (Collection<Object>) obj;
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object it : c) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(it));
            }
            sb.append("]");
            return sb.toString();
        }
        return toJson(String.valueOf(obj));
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}


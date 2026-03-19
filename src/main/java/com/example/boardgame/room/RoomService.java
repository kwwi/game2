package com.example.boardgame.room;

import com.example.boardgame.model.PieceColor;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import com.example.boardgame.dto.*;
import com.example.boardgame.dto.record.EndEvent;
import com.example.boardgame.dto.record.InitEvent;
import com.example.boardgame.dto.record.MoveEvent;
import com.example.boardgame.dto.record.RecordEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final ObjectMapper objectMapper;

    private static final long NO_PLAYER_KICK_DELAY_MS = 10L * 60L * 1000L;
    private final ScheduledExecutorService noPlayerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "room-no-player-kicker");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ScheduledFuture<?>> pendingNoPlayerKickByRoom = new ConcurrentHashMap<>();

    public RoomService(RoomEventHub eventHub, ObjectMapper objectMapper) {
        this.eventHub = eventHub;
        this.objectMapper = objectMapper;
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
                InitEvent evt = new InitEvent();
                evt.ts = Instant.now().toString();
                evt.board = r.getGame().buildBoardView();
                evt.currentTurn = r.getGame().getCurrentTurn() == null ? null : r.getGame().getCurrentTurn().toString();
                evt.winner = null;
                appendGameEvent(r, evt);
            }
        }
    }

    public List<RoomView> listRooms() {
        List<RoomView> res = new ArrayList<>();
        for (Room r : rooms.values()) {
            synchronized (r.getLock()) {
                res.add(roomView(r));
            }
        }
        return res;
    }

    public RoomView getRoom(String roomId) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            return roomView(r);
        }
    }

    public ApiResponse<RoomView> adminResetRoom(String roomId, String userId, String name) {
        if (name == null || !"hyuan".equalsIgnoreCase(name.trim())) {
            return ApiResponse.fail("无权限");
        }
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            // Kick everyone in the room.
            List<String> kicked = new ArrayList<>(r.getParticipantsById().keySet());
            r.getParticipantsById().clear();
            r.setBlackPlayerUserId(null);
            r.setWhitePlayerUserId(null);

            cancelNoPlayerKick(roomId);
            eventHub.closeRoom(roomId, "admin_reset_by_hyuan");
            for (String uid : kicked) {
                eventHub.delayCloseUser(roomId, uid, 0L, "admin_reset_by_hyuan");
            }

            // Reset to initial state and start a new record snapshot.
            r.resetGameToInitial();
            ensureRoomRecordFile(roomId);
            InitEvent evt = new InitEvent();
            evt.ts = Instant.now().toString();
            evt.board = r.getGame().buildBoardView();
            evt.currentTurn = r.getGame().getCurrentTurn() == null ? null : r.getGame().getCurrentTurn().toString();
            evt.winner = null;
            appendGameEvent(r, evt);

            return ApiResponse.ok("ok", roomView(r));
        }
    }

    public JoinRoomResponse join(String roomId, String userId, String name, RoomRole desired) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
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

                eventHub.broadcast(roomId, "room", new RoomEvent("room", roomView(r)));
                updateNoPlayerKickTimerLocked(r);
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

            eventHub.broadcast(roomId, "room", new RoomEvent("room", roomView(r)));
            updateNoPlayerKickTimerLocked(r);
            return roomJoinView(r, p);
        }
    }

    public void leave(String roomId, String userId) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            Participant p = r.getParticipantsById().remove(userId);
            if (p == null) return;

            if (userId.equals(r.getBlackPlayerUserId())) {
                r.setBlackPlayerUserId(null);
            }
            if (userId.equals(r.getWhitePlayerUserId())) {
                r.setWhitePlayerUserId(null);
            }

            if (r.getParticipantsById().isEmpty()) {
                // 所有人都退出：重置房间状态（清聊天、清邀请、棋盘初始化、胜负清空、开始新录像文件）
                resetRoomLocked(r);
                return;
            }

            // 完全退出房间：不判胜负。对局状态保持不变，等待其他用户补位后继续对弈。
            eventHub.broadcast(roomId, "room", new RoomEvent("room", roomView(r)));
            updateNoPlayerKickTimerLocked(r);
        }
        // Give the browser a short grace period to reconnect/rejoin and reuse the same SSE connection (if still open).
        eventHub.delayCloseUser(roomId, userId, 8000L, "left_room");
    }

    public ApiResponse<RoomView> joinSeat(String roomId, String userId, Seat seat) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            Participant me = r.getParticipantsById().get(userId);
            if (me == null) return ApiResponse.fail("你不在房间内");
            if (seat == null) return ApiResponse.fail("seat不能为空");
            if (me.getSeat() != null) return ApiResponse.fail("你已经是棋手");

            if (seat == Seat.BLACK) {
                if (r.getBlackPlayerUserId() != null) return ApiResponse.fail("黑棋席位已有人");
                r.setBlackPlayerUserId(userId);
            } else {
                if (r.getWhitePlayerUserId() != null) return ApiResponse.fail("白棋席位已有人");
                r.setWhitePlayerUserId(userId);
            }

            me.setRole(RoomRole.PLAYER);
            me.setSeat(seat);

            RoomView rv = roomView(r);
            eventHub.broadcast(roomId, "room", new RoomEvent("room", rv));
            eventHub.broadcast(roomId, "state", buildStateEvent(r));
            updateNoPlayerKickTimerLocked(r);
            return ApiResponse.ok("ok", rv);
        }
    }

    /**
     * 退出对弈（释放席位但留在房间作为观众），不判胜负。
     * 用于“棋手先离开对局，但房间还在，允许其它新进入用户选择接手继续对弈”。
     */
    public void leaveGameAsSpectator(String roomId, String userId) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
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
            updateNoPlayerKickTimerLocked(r);
        }
    }

    public List<ChatMessage> chatHistory(String roomId) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            return r.getChatHistory();
        }
    }

    public ChatMessage sendChat(String roomId, String userId, String content) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            Participant p = r.getParticipantsById().get(userId);
            String name = p != null ? p.getName() : "匿名";
            ChatMessage msg = new ChatMessage(UUID.randomUUID().toString(), roomId, userId, name, content, Instant.now());
            r.addChat(msg, CHAT_MAX);
            eventHub.broadcast(roomId, "chat", new ChatEvent(msg));
            return msg;
        }
    }

    public GameStateView getGameState(String roomId) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            GameStateView s = new GameStateView();
            s.room = roomView(r);
            s.board = r.getGame().buildBoardView();
            s.currentTurn = r.getGame().getCurrentTurn() == null ? null : r.getGame().getCurrentTurn().toString();
            s.winner = r.getWinner() == null ? null : r.getWinner().toString();
            return s;
        }
    }

    public GameInstance.MoveResult move(String roomId, String userId, int fromX, int fromY, int toX, int toY) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
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
                MoveEvent mevt = new MoveEvent();
                mevt.ts = Instant.now().toString();
                mevt.userId = userId;
                mevt.seat = p.getSeat() == null ? null : p.getSeat().toString();
                mevt.fromX = fromX;
                mevt.fromY = fromY;
                mevt.toX = toX;
                mevt.toY = toY;
                mevt.board = res.board;
                mevt.currentTurn = res.currentTurn == null ? null : res.currentTurn.toString();
                mevt.winner = res.winner == null ? null : res.winner.toString();
                appendGameEvent(r, mevt);
                if (res.winner != null) {
                    r.setWinner(res.winner);
                    EndEvent eevt = new EndEvent();
                    eevt.ts = Instant.now().toString();
                    eevt.winner = res.winner.toString();
                    eevt.board = res.board;
                    eevt.currentTurn = res.currentTurn == null ? null : res.currentTurn.toString();
                    appendGameEvent(r, eevt);
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
    }

    public ApiResponse<InviteView> createInvite(String roomId, String fromUserId, String toUserId, Seat seat) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            Participant from = r.getParticipantsById().get(fromUserId);
            Participant to = r.getParticipantsById().get(toUserId);
            if (from == null || to == null) {
                return ApiResponse.fail("用户不在房间内");
            }
            if (from.getRole() != RoomRole.PLAYER || from.getSeat() == null) {
                return ApiResponse.fail("只有棋手可以发起替换邀请");
            }
            if (seat == null) {
                return ApiResponse.fail("seat不能为空");
            }
            // Only allow replacing the requester's own seat
            if (from.getSeat() != seat) {
                return ApiResponse.fail("只能替换自己的席位");
            }

            // 正在对弈的双方不允许互相替换
            String otherUserId = (seat == Seat.BLACK) ? r.getWhitePlayerUserId() : r.getBlackPlayerUserId();
            if (otherUserId != null && otherUserId.equals(toUserId)) {
                return ApiResponse.fail("对弈双方不能互相替换");
            }

            String oldUserId = (seat == Seat.BLACK) ? r.getBlackPlayerUserId() : r.getWhitePlayerUserId();
            if (oldUserId == null || !oldUserId.equals(fromUserId)) {
                return ApiResponse.fail("席位信息不一致");
            }

            String inviteId = UUID.randomUUID().toString();
            Invite invite = new Invite(inviteId, roomId, fromUserId, toUserId, seat);
            r.getInvitesById().put(inviteId, invite);
            InviteView inviteView = new InviteView();
            inviteView.inviteId = invite.getInviteId();
            inviteView.roomId = invite.getRoomId();
            inviteView.fromUserId = invite.getFromUserId();
            inviteView.toUserId = invite.getToUserId();
            inviteView.seat = invite.getSeat() == null ? null : invite.getSeat().toString();
            inviteView.status = invite.getStatus() == null ? null : invite.getStatus().toString();
            inviteView.createdAt = invite.getCreatedAt() == null ? null : invite.getCreatedAt().toString();
            inviteView.resolvedAt = invite.getResolvedAt() == null ? null : invite.getResolvedAt().toString();
            // push to receiver
            eventHub.sendToUser(roomId, toUserId, "invite", new InviteEvent(inviteView));
            // also notify room list update if desired
            eventHub.broadcast(roomId, "room", of("type", "room", "room", roomView(r)));

            return ApiResponse.ok("ok", inviteView);
        }
    }

    public ApiResponse<RoomView> respondInvite(String roomId, String inviteId, String userId, boolean accept) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            Invite invite = r.getInvitesById().get(inviteId);
            if (invite == null) return ApiResponse.fail("邀请不存在");
            if (!invite.getToUserId().equals(userId)) return ApiResponse.fail("无权限响应该邀请");
            if (invite.getStatus() != InviteStatus.PENDING) return ApiResponse.fail("邀请已处理");

            if (!accept) {
                invite.setStatus(InviteStatus.DECLINED);
                invite.setResolvedAt(Instant.now());
                eventHub.sendToUser(roomId, invite.getFromUserId(), "inviteResolved", new InviteResolvedEvent(inviteId, "DECLINED"));
                return ApiResponse.ok("已拒绝", null);
            }

            // accept -> perform replace if still valid
            Participant from = r.getParticipantsById().get(invite.getFromUserId());
            Participant to = r.getParticipantsById().get(invite.getToUserId());
            if (from == null || to == null) {
                invite.setStatus(InviteStatus.CANCELLED);
                invite.setResolvedAt(Instant.now());
                return ApiResponse.fail("棋手或被邀请者已不在房间");
            }
            if (from.getRole() != RoomRole.PLAYER || from.getSeat() == null || from.getSeat() != invite.getSeat()) {
                invite.setStatus(InviteStatus.CANCELLED);
                invite.setResolvedAt(Instant.now());
                return ApiResponse.fail("邀请已失效（席位变化）");
            }
            String oldUserId = (invite.getSeat() == Seat.BLACK) ? r.getBlackPlayerUserId() : r.getWhitePlayerUserId();
            if (oldUserId == null || !oldUserId.equals(invite.getFromUserId())) {
                invite.setStatus(InviteStatus.CANCELLED);
                invite.setResolvedAt(Instant.now());
                return ApiResponse.fail("邀请已失效（席位变化）");
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
            eventHub.sendToUser(roomId, invite.getFromUserId(), "inviteResolved", new InviteResolvedEvent(inviteId, "ACCEPTED"));

            updateNoPlayerKickTimerLocked(r);
            return ApiResponse.ok("已同意并完成替换", roomView(r));
        }
    }

    private static class InviteEvent {
        public String type = "invite";
        public InviteView invite;

        public InviteEvent(InviteView invite) {
            this.invite = invite;
        }
    }

    private static class InviteResolvedEvent {
        public String type = "inviteResolved";
        public String inviteId;
        public String status;

        public InviteResolvedEvent(String inviteId, String status) {
            this.inviteId = inviteId;
            this.status = status;
        }
    }

    public List<RecordInfo> listRecords() {
        try {
            if (!Files.exists(recordsDir)) return Collections.emptyList();
            List<RecordInfo> res = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(recordsDir, "*.jsonl")) {
                for (Path p : ds) {
                    RecordInfo ri = new RecordInfo();
                    ri.id = p.getFileName().toString();
                    ri.path = p.toString();
                    res.add(ri);
                }
            }
            res.sort(Comparator.comparing(m -> String.valueOf(m.id)));
            return res;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Only return records that belong to the given user.
     * Heuristic: record JSONL lines include `"userId":"..."`
     */
    public List<RecordInfo> listMyRecords(String userId) {
        try {
            if (!Files.exists(recordsDir)) return Collections.emptyList();
            String needle = "\"userId\":\"" + escape(userId) + "\"";

            List<RecordInfo> res = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(recordsDir, "*.jsonl")) {
                for (Path p : ds) {
                    String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    if (content.contains(needle)) {
                        RecordInfo ri = new RecordInfo();
                        ri.id = p.getFileName().toString();
                        ri.path = p.toString();
                        res.add(ri);
                    }
                }
            }
            res.sort(Comparator.comparing(m -> String.valueOf(m.id)));
            return res;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public String readRecord(String recordId) throws IOException {
        Path p = recordsDir.resolve(recordId).normalize();
        if (!p.startsWith(recordsDir)) throw new IOException("invalid record id");
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    public ApiResponse<GameStateView> loadRecordIntoRoom(String recordId, String roomId) throws IOException {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            String content = readRecord(recordId);
            String[] lines = content.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                try {
                    JsonNode node = objectMapper.readTree(line);
                    JsonNode typeNode = node.get("type");
                    if (typeNode == null) continue;
                    String type = typeNode.asText(null);
                    if (!"move".equals(type)) continue;
                    MoveEvent evt = objectMapper.treeToValue(node, MoveEvent.class);
                    if (evt == null || evt.board == null) continue;
                    PieceColor turn = null;
                    if (evt.currentTurn != null) {
                        try {
                            turn = PieceColor.valueOf(String.valueOf(evt.currentTurn));
                        } catch (IllegalArgumentException ignore) {
                        }
                    }
                    GameStateView state = restoreSnapshot(roomId, evt.board, turn);
                    return ApiResponse.ok("ok", state);
                } catch (Exception ignore) {
                    // skip malformed lines
                }
            }
            // no moves: reset
            r.getGame().reset(PieceColor.BLACK);
            return ApiResponse.ok("no moves, reset", getGameState(roomId));
        }
    }

    public GameStateView restoreSnapshot(String roomId, Map<String, Object> boardView, PieceColor currentTurn) {
        Room r = requireRoom(roomId);
        synchronized (r.getLock()) {
            r.getGame().loadFromSnapshot(boardView, currentTurn);
            r.setWinner(null);
            return getGameState(roomId);
        }
    }

    // -------- helpers --------

    private Room requireRoom(String roomId) {
        Room r = rooms.get(roomId);
        if (r == null) throw new IllegalArgumentException("room not found");
        ensureRoomRecordFile(roomId);
        return r;
    }

    private RoomView roomView(Room r) {
        RoomView res = new RoomView();
        res.roomId = r.getRoomId();
        res.name = r.getName();
        res.blackPlayerUserId = r.getBlackPlayerUserId();
        res.whitePlayerUserId = r.getWhitePlayerUserId();
        res.canJoinAsPlayer = r.canJoinAsPlayer();

        List<ParticipantView> users = new ArrayList<>();
        for (Participant p : r.getParticipantsById().values()) {
            ParticipantView pv = new ParticipantView();
            pv.userId = p.getUserId();
            pv.name = p.getName();
            pv.role = p.getRole() == null ? null : p.getRole().toString();
            pv.seat = p.getSeat() == null ? null : p.getSeat().toString();
            pv.joinedAt = p.getJoinedAt() == null ? null : p.getJoinedAt().toString();
            users.add(pv);
        }
        res.participants = users;
        res.recordFile = r.getCurrentRecordFile() == null ? null : r.getCurrentRecordFile().getFileName().toString();
        res.recordStartedAt = r.getRecordStartedAt() == null ? null : r.getRecordStartedAt().toString();
        return res;
    }

    private JoinRoomResponse roomJoinView(Room r, Participant me) {
        JoinRoomResponse res = new JoinRoomResponse();
        res.success = true;
        MeView mv = new MeView();
        mv.userId = me.getUserId();
        mv.name = me.getName();
        mv.role = me.getRole() == null ? null : me.getRole().toString();
        mv.seat = me.getSeat() == null ? null : me.getSeat().toString();
        res.me = mv;
        res.room = roomView(r);
        res.state = getGameState(r.getRoomId());
        return res;
    }

    // ----- SSE payloads (avoid Map hardcoding) -----
    private static class RoomEvent {
        public String type;
        public RoomView room;

        public RoomEvent(String type, RoomView room) {
            this.type = type;
            this.room = room;
        }
    }

    private static class ChatEvent {
        public String type = "chat";
        public ChatMessage message;

        public ChatEvent(ChatMessage message) {
            this.message = message;
        }
    }

    private static class StateEvent {
        public String type = "state";
        public RoomView room;
        public Map<String, Object> board;
        public String currentTurn;
        public String winner;
    }

    private StateEvent buildStateEvent(Room r) {
        StateEvent e = new StateEvent();
        e.room = roomView(r);
        e.board = r.getGame().buildBoardView();
        e.currentTurn = r.getGame().getCurrentTurn() == null ? null : r.getGame().getCurrentTurn().toString();
        e.winner = r.getWinner() == null ? null : r.getWinner().toString();
        return e;
    }

    private void ensureRoomRecordFile(String roomId) {
        Room r = rooms.get(roomId);
        if (r == null) return;
        synchronized (r.getLock()) {
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
    }

    private void appendGameEvent(Room r, RecordEvent event) {
        if (event == null || event.type == null) return;
        String type = String.valueOf(event.type);
        if (!("init".equals(type) || "move".equals(type) || "end".equals(type) || "quit".equals(type))) return;
        appendRecordEvent(r, event);
    }

    private void resetRoomLocked(Room r) {
        // caller must hold r.getLock()
        String roomId = r.getRoomId();
        cancelNoPlayerKick(roomId);
        // Proactively close any lingering SSE connections for this room.
        // (This may happen if browsers still hold EventSource connections.)
        eventHub.closeRoom(roomId, "room_reset_no_participants");
        r.resetGameToInitial();
        ensureRoomRecordFile(roomId);
        InitEvent evt = new InitEvent();
        evt.ts = Instant.now().toString();
        evt.board = r.getGame().buildBoardView();
        evt.currentTurn = r.getGame().getCurrentTurn() == null ? null : r.getGame().getCurrentTurn().toString();
        evt.winner = null;
        appendGameEvent(r, evt);
        // No one should be connected now; broadcasts are harmless.
        eventHub.broadcast(roomId, "room", of("type", "room", "room", roomView(r)));
        eventHub.broadcast(roomId, "state", of(
                "type", "state",
                "board", r.getGame().buildBoardView(),
                "currentTurn", r.getGame().getCurrentTurn().toString(),
                "winner", null,
                "room", roomView(r)
        ));
    }

    private void updateNoPlayerKickTimerLocked(Room r) {
        // caller must hold r.getLock()
        String roomId = r.getRoomId();
        boolean noPlayers = r.getBlackPlayerUserId() == null && r.getWhitePlayerUserId() == null;
        if (!noPlayers) {
            cancelNoPlayerKick(roomId);
            return;
        }
        if (pendingNoPlayerKickByRoom.containsKey(roomId)) {
            return;
        }
        ScheduledFuture<?> f = noPlayerScheduler.schedule(() -> kickSpectatorsAndResetIfStillNoPlayers(roomId),
                NO_PLAYER_KICK_DELAY_MS, TimeUnit.MILLISECONDS);
        pendingNoPlayerKickByRoom.put(roomId, f);
    }

    private void cancelNoPlayerKick(String roomId) {
        ScheduledFuture<?> f = pendingNoPlayerKickByRoom.remove(roomId);
        if (f != null) {
            f.cancel(false);
        }
    }

    private void kickSpectatorsAndResetIfStillNoPlayers(String roomId) {
        pendingNoPlayerKickByRoom.remove(roomId);
        Room r = rooms.get(roomId);
        if (r == null) return;
        synchronized (r.getLock()) {
            boolean noPlayers = r.getBlackPlayerUserId() == null && r.getWhitePlayerUserId() == null;
            if (!noPlayers) return;

            // Kick everyone currently in the room (all are spectators when noPlayers is true).
            List<String> toKick = new ArrayList<>(r.getParticipantsById().keySet());
            r.getParticipantsById().clear();

            // Close SSE connections immediately.
            eventHub.closeRoom(roomId, "kicked_no_players_timeout");
            for (String userId : toKick) {
                // best-effort: if any emitter reappears, close it too
                eventHub.delayCloseUser(roomId, userId, 0L, "kicked_no_players_timeout");
            }

            // Reset game state to initial.
            r.resetGameToInitial();
            ensureRoomRecordFile(roomId);
            InitEvent evt = new InitEvent();
            evt.ts = Instant.now().toString();
            evt.board = r.getGame().buildBoardView();
            evt.currentTurn = r.getGame().getCurrentTurn() == null ? null : r.getGame().getCurrentTurn().toString();
            evt.winner = null;
            appendGameEvent(r, evt);
        }
    }

    private void appendRecordEvent(Room r, RecordEvent event) {
        ensureRoomRecordFile(r.getRoomId());
        Path p = r.getCurrentRecordFile();
        if (p == null) return;
        try {
            String json = objectMapper.writeValueAsString(event);
            BufferedWriter writer = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            try {
                writer.write(json);
                writer.newLine();
            } finally {
                writer.close();
            }
        } catch (IOException ignore) {
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}


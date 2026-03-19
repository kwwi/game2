package com.example.boardgame.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import static com.example.boardgame.util.Maps.of;

@Component
public class RoomEventHub {

    public static class Client {
        public final String roomId;
        public final String userId;
        public final SseEmitter emitter;

        public Client(String roomId, String userId, SseEmitter emitter) {
            this.roomId = roomId;
            this.userId = userId;
            this.emitter = emitter;
        }
    }

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, Client>> clientsByRoom = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingCloseByRoomUser = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "room-sse-cleaner");
        t.setDaemon(true);
        return t;
    });

    public RoomEventHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter connect(String roomId, String userId) {
        cancelDelayedClose(roomId, userId);
        SseEmitter emitter = new SseEmitter(0L);
        Client client = new Client(roomId, userId, emitter);
        clientsByRoom.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(userId, client);

        emitter.onCompletion(() -> disconnect(roomId, userId));
        emitter.onTimeout(() -> disconnect(roomId, userId));
        emitter.onError((e) -> disconnect(roomId, userId));

        // initial ping
        sendToUser(roomId, userId, "hello", of("ts", Instant.now().toString()));
        return emitter;
    }

    public void disconnect(String roomId, String userId) {
        cancelDelayedClose(roomId, userId);
        Map<String, Client> room = clientsByRoom.get(roomId);
        if (room == null) return;
        room.remove(userId);
        if (room.isEmpty()) clientsByRoom.remove(roomId);
    }

    /**
     * Schedule closing a user's SSE connection after a short grace period.
     * If the user re-joins quickly (and the SSE is still open), we cancel and "reuse" it.
     */
    public void delayCloseUser(String roomId, String userId, long delayMs, String reason) {
        String key = roomId + "|" + userId;
        cancelDelayedClose(roomId, userId);
        ScheduledFuture<?> f = scheduler.schedule(() -> {
            // If still connected, close it.
            Client c = null;
            Map<String, Client> room = clientsByRoom.get(roomId);
            if (room != null) {
                c = room.get(userId);
            }
            if (c != null) {
                try {
                    safeSend(c, "close", of("type", "close", "reason", reason, "roomId", roomId));
                } catch (Exception ignore) {
                }
                try {
                    c.emitter.complete();
                } catch (Exception ignore) {
                }
                disconnect(roomId, userId);
            }
            pendingCloseByRoomUser.remove(key);
        }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
        pendingCloseByRoomUser.put(key, f);
    }

    public void cancelDelayedClose(String roomId, String userId) {
        String key = roomId + "|" + userId;
        ScheduledFuture<?> f = pendingCloseByRoomUser.remove(key);
        if (f != null) {
            f.cancel(false);
        }
    }

    /**
     * Force-close and remove all SSE connections in a room.
     * Used when a room is reset with no participants, to avoid leaking emitters.
     */
    public void closeRoom(String roomId, String reason) {
        Map<String, Client> room = clientsByRoom.remove(roomId);
        if (room == null) return;
        for (String userId : new ArrayList<>(room.keySet())) {
            cancelDelayedClose(roomId, userId);
        }
        for (Client c : new ArrayList<>(room.values())) {
            try {
                // best-effort notify client before closing
                if (reason != null && !reason.isEmpty()) {
                    safeSend(c, "close", of("type", "close", "reason", reason, "roomId", roomId));
                }
            } catch (Exception ignore) {
            }
            try {
                c.emitter.complete();
            } catch (Exception ignore) {
            }
        }
        room.clear();
    }

    public void broadcast(String roomId, String event, Object data) {
        Map<String, Client> room = clientsByRoom.get(roomId);
        if (room == null) return;
        for (Client c : new ArrayList<>(room.values())) {
            safeSend(c, event, data);
        }
    }

    public void sendToUser(String roomId, String userId, String event, Object data) {
        Map<String, Client> room = clientsByRoom.get(roomId);
        if (room == null) return;
        Client c = room.get(userId);
        if (c == null) return;
        safeSend(c, event, data);
    }

    private void safeSend(Client c, String event, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            c.emitter.send(SseEmitter.event().name(event).data(json));
        } catch (IOException e) {
            disconnect(c.roomId, c.userId);
        }
    }
}


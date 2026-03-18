package com.example.boardgame.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    public RoomEventHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter connect(String roomId, String userId) {
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
        Map<String, Client> room = clientsByRoom.get(roomId);
        if (room == null) return;
        room.remove(userId);
        if (room.isEmpty()) clientsByRoom.remove(roomId);
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


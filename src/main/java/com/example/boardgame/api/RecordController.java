package com.example.boardgame.api;

import com.example.boardgame.model.PieceColor;
import com.example.boardgame.room.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.List;
import static com.example.boardgame.util.Maps.of;

@RestController
@RequestMapping("/api/records")
@CrossOrigin
public class RecordController {

    private final RoomService roomService;
    private final ObjectMapper objectMapper;

    public RecordController(RoomService roomService, ObjectMapper objectMapper) {
        this.roomService = roomService;
        this.objectMapper = objectMapper;
    }

    /**
     * Load a record into a room and continue.
     * Strategy: find last move event line, parse it, restore board snapshot + currentTurn.
     */
    @PostMapping("/{recordId}/load")
    public Map<String, Object> load(@PathVariable String recordId, @RequestParam String roomId) throws IOException {
        Map<String, Object> res = roomService.loadRecordIntoRoom(recordId, roomId);
        Object raw = res.get("rawEvent");
        if (!(raw instanceof String)) {
            return res;
        }
        String rawLine = (String) raw;
        @SuppressWarnings("unchecked")
        Map<String, Object> evt = objectMapper.readValue(rawLine, Map.class);
        Object boardObj = evt.get("board");
        Object turnObj = evt.get("currentTurn");
        PieceColor turn = null;
        if (turnObj != null) {
            try {
                turn = PieceColor.valueOf(String.valueOf(turnObj));
            } catch (IllegalArgumentException ignore) {
            }
        }
        if (boardObj instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> board = (Map<String, Object>) boardObj;
            Map<String, Object> state = roomService.restoreSnapshot(roomId, board, turn);
            return of("success", true, "message", "ok", "state", state);
        }
        return of("success", false, "message", "record has no snapshot");
    }

    /**
     * List records owned by current user (heuristic filter by `"userId":"..."`).
     */
    @GetMapping("/mine")
    public List<Map<String, Object>> mine(@RequestParam String userId) {
        return roomService.listMyRecords(userId);
    }
}


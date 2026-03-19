package com.example.boardgame.api;

import com.example.boardgame.room.RoomService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import com.example.boardgame.dto.ApiResponse;
import com.example.boardgame.dto.GameStateView;
import com.example.boardgame.dto.RecordInfo;

@RestController
@RequestMapping("/api/records")
@CrossOrigin
public class RecordController {

    private final RoomService roomService;

    public RecordController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * Load a record into a room and continue.
     * Strategy: find last move event line, parse it, restore board snapshot + currentTurn.
     */
    @PostMapping("/{recordId}/load")
    public ApiResponse<GameStateView> load(@PathVariable String recordId, @RequestParam String roomId) throws IOException {
        return roomService.loadRecordIntoRoom(recordId, roomId);
    }

    /**
     * List records owned by current user (heuristic filter by `"userId":"..."`).
     */
    @GetMapping("/mine")
    public List<RecordInfo> mine(@RequestParam String userId) {
        return roomService.listMyRecords(userId);
    }
}


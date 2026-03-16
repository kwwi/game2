package com.example.boardgame.api;

import com.example.boardgame.model.Board;
import com.example.boardgame.model.PieceColor;
import com.example.boardgame.service.GameService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
@CrossOrigin
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/state")
    public Map<String, Object> getState() {
        Map<String, Object> res = new HashMap<>();
        res.put("board", gameService.getBoard());
        res.put("currentTurn", gameService.getCurrentTurn());
        res.put("winner", null);
        return res;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestParam(defaultValue = "BLACK") PieceColor firstPlayer) {
        gameService.reset(firstPlayer);
        return getState();
    }

    public static class MoveRequest {
        public int fromX;
        public int fromY;
        public int toX;
        public int toY;
    }

    @PostMapping("/move")
    public GameService.MoveResult move(@RequestBody MoveRequest req) {
        return gameService.move(req.fromX, req.fromY, req.toX, req.toY);
    }
}


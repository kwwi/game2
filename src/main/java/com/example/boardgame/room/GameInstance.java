package com.example.boardgame.room;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.boardgame.model.Board;
import com.example.boardgame.model.PieceColor;
import com.example.boardgame.model.Position;

/**
 * Per-room game state (board + turn + rules).
 */
public class GameInstance {

    private Board      board       = new Board();
    private PieceColor currentTurn = PieceColor.BLACK;

    public Board getBoard() {
        return board;
    }

    public PieceColor getCurrentTurn() {
        return currentTurn;
    }

    public void reset(PieceColor firstPlayer) {
        this.board = new Board();
        this.currentTurn = firstPlayer;
    }

    public void loadFromSnapshot(Map<String, Object> boardView, PieceColor currentTurn) {
        // Minimal "continue" support: re-create board and set pieces based on snapshot pieces.
        // Board topology comes from cc file, so we only restore pieces.
        Board b = new Board();
        Object piecesObj = boardView.get("pieces");
        if (piecesObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> pieces = (Map<Object, Object>) piecesObj;
            b.getPieces().clear();
            for (Map.Entry<?, ?> e : pieces.entrySet()) {
                String key = String.valueOf(e.getKey());
                String[] parts = key.split(",");
                if (parts.length != 2)
                    continue;
                int x;
                int y;
                try {
                    x = Integer.parseInt(parts[0].trim());
                    y = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ex) {
                    continue;
                }
                Position p = new Position(x, y);
                if (!b.isInside(p))
                    continue;
                PieceColor color = null;
                Object v = e.getValue();
                if (v instanceof PieceColor) {
                    color = (PieceColor) v;
                } else if (v != null) {
                    try {
                        color = PieceColor.valueOf(String.valueOf(v));
                    } catch (IllegalArgumentException ignore) {}
                }
                if (color != null) {
                    b.setPiece(p, color);
                }
            }
        }
        this.board = b;
        this.currentTurn = currentTurn == null ? PieceColor.BLACK : currentTurn;
    }

    public static class MoveResult {
        public boolean             success;
        public String              message;
        public Map<String, Object> board;
        public PieceColor          currentTurn;
        public PieceColor          winner;
    }

    public MoveResult move(int fromX, int fromY, int toX, int toY) {
        MoveResult result = new MoveResult();
        Position from = new Position(fromX, fromY);
        Position to = new Position(toX, toY);

        if (!board.isInside(from) || !board.isInside(to)) {
            result.success = false;
            result.message = "坐标不合法";
            fillState(result);
            return result;
        }

        PieceColor piece = board.getPiece(from);
        if (piece == null || piece != currentTurn) {
            result.success = false;
            result.message = "必须移动当前回合玩家自己的棋子";
            fillState(result);
            return result;
        }

        if (board.getPiece(to) != null) {
            result.success = false;
            result.message = "目标位置必须为空";
            fillState(result);
            return result;
        }

        if (!board.getNeighbors(from).contains(to)) {
            result.success = false;
            result.message = "两个落子点之间没有线，不能移动";
            fillState(result);
            return result;
        }

        board.setPiece(from, null);
        board.setPiece(to, piece);

        applyRecursiveCaptures(to, piece);

        PieceColor winner = checkWinner();
        if (winner == null) {
            currentTurn = (currentTurn == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;
        }

        result.success = true;
        result.message = "ok";
        result.board = buildBoardView();
        result.currentTurn = currentTurn;
        result.winner = winner;
        return result;
    }

    private void fillState(MoveResult result) {
        result.board = buildBoardView();
        result.currentTurn = currentTurn;
        result.winner = checkWinner();
    }

    public Map<String, Object> buildBoardView() {
        Map<String, Object> view = new HashMap<>();
        view.put("pieces", board.getSerializablePieces());
        view.put("edges", board.getSerializableEdges());
        return view;
    }

    private void applyRecursiveCaptures(Position lastMovePos, PieceColor moverColor) {
        Set<Position> frontier = new HashSet<>();
        frontier.add(lastMovePos);

        boolean changed;
        do {
            CapturePass pass = applySinglePassCaptures(frontier, moverColor);
            changed = pass.changed;
            frontier = pass.nextFrontier;
        } while (changed && !frontier.isEmpty());
    }

    private static class CapturePass {
        boolean       changed;
        Set<Position> nextFrontier = new HashSet<>();
    }

    private CapturePass applySinglePassCaptures(Set<Position> anchors, PieceColor moverColor) {
        CapturePass pass = new CapturePass();
        Map<Position, PieceColor> snapshot = new HashMap<>(board.getPieces());
        Set<Position> toFlip = new HashSet<>();

        int[][] dirs = {
                         { 1, 0 },
                         { 0, 1 },
                         { -1, 0 },
                         { 0, -1 },
                         { 1, 1 },
                         { 1, -1 },
                         { -1, -1 },
                         { -1, 1 }
        };

        for (Position a : anchors) {
            PieceColor aColor = snapshot.get(a);
            if (aColor != moverColor)
                continue;

            for (int[] d : dirs) {
                int dx = d[0];
                int dy = d[1];

                // 夹：A-B-C，翻转 B
                Position b = new Position(a.getX() + dx, a.getY() + dy);
                Position c = new Position(a.getX() + 2 * dx, a.getY() + 2 * dy);
                if (board.isInside(b) && board.isInside(c) && board.getNeighbors(a).contains(b) && board.getNeighbors(b).contains(c)) {
                    PieceColor bColor = snapshot.get(b);
                    PieceColor cColor = snapshot.get(c);
                    if (bColor != null && bColor != moverColor && cColor == moverColor) {
                        toFlip.add(b);
                    }
                }

                // 挑：B-A-C，翻转两边 B、C
                Position left = new Position(a.getX() - dx, a.getY() - dy);
                Position right = new Position(a.getX() + dx, a.getY() + dy);
                if (board.isInside(left) && board.isInside(right) && board.getNeighbors(left).contains(a) && board.getNeighbors(a).contains(right)) {
                    PieceColor lColor = snapshot.get(left);
                    PieceColor rColor = snapshot.get(right);
                    if (lColor != null && lColor != moverColor && rColor != null && rColor != moverColor) {
                        toFlip.add(left);
                        toFlip.add(right);
                    }
                }
            }
        }

        if (!toFlip.isEmpty()) {
            for (Position pos : toFlip) {
                board.setPiece(pos, moverColor);
                pass.nextFrontier.add(pos);
            }
            pass.changed = true;
        }
        return pass;
    }

    private PieceColor checkWinner() {
        boolean hasBlack = false;
        boolean hasWhite = false;
        for (PieceColor color : board.getPieces().values()) {
            if (color == PieceColor.BLACK)
                hasBlack = true;
            if (color == PieceColor.WHITE)
                hasWhite = true;
        }
        if (!hasBlack && hasWhite)
            return PieceColor.WHITE;
        if (!hasWhite && hasBlack)
            return PieceColor.BLACK;
        return null;
    }
}

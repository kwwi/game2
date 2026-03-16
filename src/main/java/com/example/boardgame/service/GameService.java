package com.example.boardgame.service;

import com.example.boardgame.model.Board;
import com.example.boardgame.model.PieceColor;
import com.example.boardgame.model.Position;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GameService {

    private Board board = new Board();
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

    public static class MoveResult {
        public boolean success;
        public String message;
        public java.util.Map<String, Object> board;
        public PieceColor currentTurn;
        public PieceColor winner;
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

        // perform move
        board.setPiece(from, null);
        board.setPiece(to, piece);

        // apply recursive capture (夹/挑)
        applyRecursiveCaptures(to, piece);

        // check winner
        PieceColor winner = checkWinner();

        if (winner == null) {
            // switch turn if no winner
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

    private Map<String, Object> buildBoardView() {
        Map<String, Object> view = new HashMap<>();
        view.put("pieces", board.getSerializablePieces());
        view.put("edges", board.getSerializableEdges());
        return view;
    }

    private void applyRecursiveCaptures(Position lastMovePos, PieceColor moverColor) {
        boolean changed;
        do {
            changed = applySinglePassCaptures(moverColor);
        } while (changed);
    }

    private boolean applySinglePassCaptures(PieceColor moverColor) {
        boolean changed = false;
        Map<Position, PieceColor> snapshot = new HashMap<>(board.getPieces());
        Set<Position> toFlip = new HashSet<>();

        for (Map.Entry<Position, PieceColor> entry : snapshot.entrySet()) {
            Position p = entry.getKey();
            PieceColor color = entry.getValue();
            if (color != moverColor) continue;

            // check lines through p in 8 directions: (dx,dy)
            int[][] dirs = {
                    {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                    {1, 1}, {-1, -1}, {1, -1}, {-1, 1}
            };

            for (int[] d : dirs) {
                int dx = d[0];
                int dy = d[1];

                Position pMinus = new Position(p.getX() - dx, p.getY() - dy);
                Position pPlus = new Position(p.getX() + dx, p.getY() + dy);

                if (!board.isInside(pMinus) || !board.isInside(pPlus)) continue;

                if (!areColinearNeighbors(pMinus, p, pPlus, dx, dy)) continue;

                PieceColor cMinus = snapshot.get(pMinus);
                PieceColor cPlus = snapshot.get(pPlus);

                // 夹: moverColor - opponent - moverColor
                if (cMinus == moverColor && cPlus != null && cPlus != moverColor) {
                    toFlip.add(pPlus);
                }
                if (cPlus == moverColor && cMinus != null && cMinus != moverColor) {
                    toFlip.add(pMinus);
                }

                // 挑: opponent - moverColor - opponent
                if (cMinus != null && cMinus != moverColor && cPlus != null && cPlus != moverColor) {
                    toFlip.add(pMinus);
                    toFlip.add(pPlus);
                }
            }
        }

        if (!toFlip.isEmpty()) {
            for (Position pos : toFlip) {
                board.setPiece(pos, moverColor);
            }
            changed = true;
        }
        return changed;
    }

    private boolean areColinearNeighbors(Position a, Position b, Position c, int dx, int dy) {
        // points must be exactly one step apart along (dx,dy) direction and on same straight line
        if (!board.getNeighbors(a).contains(b)) return false;
        if (!board.getNeighbors(b).contains(c)) return false;
        int abx = b.getX() - a.getX();
        int aby = b.getY() - a.getY();
        int bcx = c.getX() - b.getX();
        int bcy = c.getY() - b.getY();
        return abx == dx && aby == dy && bcx == dx && bcy == dy;
    }

    private PieceColor checkWinner() {
        boolean hasBlack = false;
        boolean hasWhite = false;
        for (PieceColor color : board.getPieces().values()) {
            if (color == PieceColor.BLACK) hasBlack = true;
            if (color == PieceColor.WHITE) hasWhite = true;
        }
        if (!hasBlack && hasWhite) return PieceColor.WHITE;
        if (!hasWhite && hasBlack) return PieceColor.BLACK;
        if (!hasBlack && !hasWhite) return null;
        return null;
    }
}


package com.example.boardgame.model;

import java.util.*;

public class Board {

    // 9x9 logical coordinates, some blocked
    public static final int SIZE = 9;

    // null = empty; otherwise color of piece
    private final Map<Position, PieceColor> pieces = new HashMap<>();

    // adjacency graph for legal moves
    private final Map<Position, List<Position>> adjacency = new HashMap<>();

    public Board() {
        initGraph();
        initPieces();
    }

    public Map<Position, PieceColor> getPieces() {
        return pieces;
    }

    public PieceColor getPiece(Position p) {
        return pieces.get(p);
    }

    public void setPiece(Position p, PieceColor color) {
        if (color == null) {
            pieces.remove(p);
        } else {
            pieces.put(p, color);
        }
    }

    public boolean isBlocked(Position p) {
        int x = p.getX();
        int y = p.getY();
        // hard-code blocked points according to description
        return (x == 1 && (y == 1 || y == 2 || y == 3 || y == 4 || y == 6 || y == 7 || y == 8 || y == 9))
                || (x == 2 && (y == 1 || y == 2 || y == 3 || y == 7 || y == 8 || y == 9));
    }

    public boolean isInside(Position p) {
        return p.getX() >= 1 && p.getX() <= SIZE && p.getY() >= 1 && p.getY() <= SIZE && !isBlocked(p);
    }

    public List<Position> getNeighbors(Position p) {
        return adjacency.getOrDefault(p, Collections.emptyList());
    }

    private void initGraph() {
        // Build graph for all usable points. To keep it simple and adjustable,
        // we connect 4-neighborhood (up/down/left/right) and diagonals,
        // but only when both endpoints are inside and not blocked.
        for (int x = 1; x <= SIZE; x++) {
            for (int y = 1; y <= SIZE; y++) {
                Position p = new Position(x, y);
                if (!isInside(p)) continue;
                adjacency.putIfAbsent(p, new ArrayList<>());
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        Position q = new Position(x + dx, y + dy);
                        if (isInside(q)) {
                            adjacency.get(p).add(q);
                        }
                    }
                }
            }
        }
        // Add extra diamond connections: [1,5]-[2,4]-[3,5]-[2,6]-[1,5]
        Position p15 = new Position(1, 5);
        Position p24 = new Position(2, 4);
        Position p35 = new Position(3, 5);
        Position p26 = new Position(2, 6);
        connectBidirectional(p15, p24);
        connectBidirectional(p24, p35);
        connectBidirectional(p35, p26);
        connectBidirectional(p26, p15);
    }

    private void connectBidirectional(Position a, Position b) {
        if (!isInside(a) || !isInside(b)) return;
        adjacency.putIfAbsent(a, new ArrayList<>());
        adjacency.putIfAbsent(b, new ArrayList<>());
        if (!adjacency.get(a).contains(b)) {
            adjacency.get(a).add(b);
        }
        if (!adjacency.get(b).contains(a)) {
            adjacency.get(b).add(a);
        }
    }

    private void initPieces() {
        // Black: [1,1]...[9,1]
        for (int x = 1; x <= 9; x++) {
            Position p = new Position(x, 1);
            setPiece(p, PieceColor.BLACK);
        }
        // White: [1,9]...[9,9]
        for (int x = 1; x <= 9; x++) {
            Position p = new Position(x, 9);
            setPiece(p, PieceColor.WHITE);
        }
    }
}


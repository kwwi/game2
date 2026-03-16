package com.example.boardgame.model;

import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Board {

    // 所有有效落子点（直接使用 cc 文件中的坐标系）
    private final Set<Position> points = new HashSet<>();

    // null = empty; otherwise color of piece
    private final Map<Position, PieceColor> pieces = new HashMap<>();

    // adjacency graph for legal moves（直接使用 cc 中的连线）
    private final Map<Position, List<Position>> adjacency = new HashMap<>();

    public Board() {
        initGraph();
        initPieces();
    }

    public Map<Position, PieceColor> getPieces() {
        return pieces;
    }

    /**
     * View of pieces for JSON serialization.
     * Key format: "x,y", e.g. "3,5".
     */
    public Map<String, PieceColor> getSerializablePieces() {
        Map<String, PieceColor> result = new HashMap<>();
        for (Map.Entry<Position, PieceColor> entry : pieces.entrySet()) {
            Position p = entry.getKey();
            PieceColor color = entry.getValue();
            if (p != null && color != null) {
                String key = p.getX() + "," + p.getY();
                result.put(key, color);
            }
        }
        return result;
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

    public boolean isInside(Position p) {
        return points.contains(p);
    }

    public List<Position> getNeighbors(Position p) {
        return adjacency.getOrDefault(p, Collections.emptyList());
    }

    private void initGraph() {
        // 根据根目录下 cc 文件构建邻接表。
        // 坐标系直接使用 cc 中的坐标（例如 [2,0]、[9,8] 等），不再做转换。
        Path path = Paths.get("cc");
        if (!Files.exists(path)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                int lb = line.indexOf('[');
                int rb = line.indexOf(']');
                if (lb < 0 || rb < 0 || rb <= lb + 1) {
                    continue;
                }
                String baseCoord = line.substring(lb + 1, rb);
                Position base = parseCcPosition(baseCoord);
                if (base == null) {
                    continue;
                }
                points.add(base);
                adjacency.putIfAbsent(base, new ArrayList<>());

                int colonIdx = line.indexOf(':', rb);
                if (colonIdx < 0 || colonIdx + 1 >= line.length()) {
                    continue;
                }
                String rest = line.substring(colonIdx + 1).trim();
                if (rest.isEmpty()) {
                    continue;
                }
                String[] tokens = rest.split("\\s+");
                for (String token : tokens) {
                    token = token.trim();
                    if (token.isEmpty()) {
                        continue;
                    }
                    int tlb = token.indexOf('[');
                    int trb = token.indexOf(']');
                    if (tlb < 0 || trb < 0 || trb <= tlb + 1) {
                        continue;
                    }
                    String coord = token.substring(tlb + 1, trb);
                    Position neighbor = parseCcPosition(coord);
                    if (neighbor == null) {
                        continue;
                    }
                    points.add(neighbor);
                    connectBidirectional(base, neighbor);
                }
            }
        } catch (IOException e) {
            // 读取失败时保持空图，避免中断程序
        }
    }

    private void connectBidirectional(Position a, Position b) {
        if (!points.contains(a) || !points.contains(b)) return;
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
        // 初始化黑棋：[2,0]..[10,0]
        for (int x = 2; x <= 10; x++) {
            Position p = new Position(x, 0);
            if (points.contains(p)) {
                setPiece(p, PieceColor.BLACK);
            }
        }
        // 初始化白棋：[2,8]..[10,8]
        for (int x = 2; x <= 10; x++) {
            Position p = new Position(x, 8);
            if (points.contains(p)) {
                setPiece(p, PieceColor.WHITE);
            }
        }
    }

    private Position parseCcPosition(String coord) {
        String[] parts = coord.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            return new Position(x, y);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 为前端返回所有边（无向），格式："x1,y1-x2,y2"
     */
    public List<String> getSerializableEdges() {
        Set<String> uniq = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (Map.Entry<Position, List<Position>> entry : adjacency.entrySet()) {
            Position from = entry.getKey();
            for (Position to : entry.getValue()) {
                if (!isInside(from) || !isInside(to)) continue;
                String a = from.getX() + "," + from.getY();
                String b = to.getX() + "," + to.getY();
                String key;
                if (a.compareTo(b) <= 0) {
                    key = a + "-" + b;
                } else {
                    key = b + "-" + a;
                }
                if (uniq.add(key)) {
                    result.add(key);
                }
            }
        }
        return result;
    }
}


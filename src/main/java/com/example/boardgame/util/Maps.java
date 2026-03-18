package com.example.boardgame.util;

import java.util.HashMap;
import java.util.Map;

public final class Maps {
    private Maps() {}

    public static Map<String, Object> of(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        if (kv == null) return m;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object k = kv[i];
            Object v = kv[i + 1];
            if (k == null) continue;
            m.put(String.valueOf(k), v);
        }
        return m;
    }
}


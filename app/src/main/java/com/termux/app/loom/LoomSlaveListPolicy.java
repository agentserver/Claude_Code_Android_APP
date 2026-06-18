package com.termux.app.loom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LoomSlaveListPolicy {

    private LoomSlaveListPolicy() {
    }

    public static List<LoomSlave> visibleSlaves(List<LoomSlave> slaves) {
        List<LoomSlave> out = new ArrayList<>();
        if (slaves == null || slaves.isEmpty()) return out;

        LinkedHashMap<String, LoomSlave> byName = new LinkedHashMap<>();
        for (LoomSlave slave : slaves) {
            if (slave == null) continue;
            String key = displayKey(slave);
            LoomSlave current = byName.get(key);
            if (current == null) {
                byName.put(key, slave);
            } else if (isBetterVisibleEntry(slave, current)) {
                byName.put(key, slave);
            }
        }

        for (Map.Entry<String, LoomSlave> entry : byName.entrySet()) {
            out.add(entry.getValue());
        }
        return out;
    }

    public static boolean isAgentUsableStatus(String status) {
        return LoomSlaveStatus.RUNNING.equals(LoomSlaveStatus.normalize(status));
    }

    private static boolean isBetterVisibleEntry(LoomSlave candidate, LoomSlave current) {
        int candidateScore = statusScore(candidate.status);
        int currentScore = statusScore(current.status);
        if (candidateScore != currentScore) return candidateScore > currentScore;
        return candidate.updatedAtMillis >= current.updatedAtMillis;
    }

    private static int statusScore(String status) {
        String normalized = LoomSlaveStatus.normalize(status);
        if (LoomSlaveStatus.RUNNING.equals(normalized)) return 5;
        if (LoomSlaveStatus.AUTH_REQUIRED.equals(normalized)) return 4;
        if (LoomSlaveStatus.STARTING.equals(normalized)) return 3;
        if (LoomSlaveStatus.PAUSED.equals(normalized)) return 2;
        if (LoomSlaveStatus.STOPPED.equals(normalized)) return 1;
        return 0;
    }

    private static String displayKey(LoomSlave slave) {
        String name = clean(slave.displayName);
        if (name.isEmpty()) name = clean(slave.name);
        if (name.isEmpty()) name = clean(slave.id);
        return name.toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

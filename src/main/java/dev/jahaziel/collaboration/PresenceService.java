/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * In-memory presence registry. Heartbeats every 30 seconds keep an entry alive; entries expire
 * after 90 seconds. Presence is advisory only and is never consulted for authorization.
 */
@Service
public final class PresenceService {
    public static final long HEARTBEAT_MS = 30000L;
    public static final long EXPIRY_MS = 90000L;

    private final Map<String, Presence> active = new HashMap<String, Presence>();

    private static String key(String workspaceId, String userId) {
        return workspaceId + "|" + userId;
    }

    public synchronized Presence join(String workspaceId, String userId, long nowMillis) {
        Presence entry = new Presence(workspaceId, userId, nowMillis);
        active.put(key(workspaceId, userId), entry);
        return snapshot(entry);
    }

    public synchronized Presence heartbeat(String workspaceId, String userId, long nowMillis) {
        Presence entry = active.get(key(workspaceId, userId));
        if (entry == null) {
            entry = new Presence(workspaceId, userId, nowMillis);
            active.put(key(workspaceId, userId), entry);
        } else {
            entry.lastHeartbeat = nowMillis;
        }
        return snapshot(entry);
    }

    public synchronized void leave(String workspaceId, String userId) {
        active.remove(key(workspaceId, userId));
    }

    public synchronized Presence activeFile(
            String workspaceId, String userId, String path, long nowMillis) {
        Presence entry = active.get(key(workspaceId, userId));
        if (entry == null) {
            entry = new Presence(workspaceId, userId, nowMillis);
            active.put(key(workspaceId, userId), entry);
        }
        entry.activeFile = WorkspacePaths.validate(path);
        entry.lastHeartbeat = nowMillis;
        return snapshot(entry);
    }

    public synchronized Presence cursor(
            String workspaceId, String userId, int line, int column, long nowMillis) {
        Presence entry = active.get(key(workspaceId, userId));
        if (entry == null) {
            entry = new Presence(workspaceId, userId, nowMillis);
            active.put(key(workspaceId, userId), entry);
        }
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("line and column must be positive");
        }
        entry.line = line;
        entry.column = column;
        entry.lastHeartbeat = nowMillis;
        return snapshot(entry);
    }

    /** Returns non-expired entries after purging stale ones. */
    public synchronized List<Presence> active(String workspaceId, long nowMillis) {
        expire(nowMillis);
        List<Presence> found = new ArrayList<Presence>();
        Iterator<Map.Entry<String, Presence>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Presence entry = iterator.next().getValue();
            if (entry.workspaceId.equals(workspaceId)) {
                found.add(snapshot(entry));
            }
        }
        return found;
    }

    /** Removes entries whose last heartbeat is older than the expiry window. */
    public synchronized int expire(long nowMillis) {
        int removed = 0;
        Iterator<Map.Entry<String, Presence>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Presence entry = iterator.next().getValue();
            if (nowMillis - entry.lastHeartbeat > EXPIRY_MS) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    private static Presence snapshot(Presence entry) {
        return new Presence(
                entry.workspaceId,
                entry.userId,
                entry.activeFile,
                entry.line,
                entry.column,
                entry.lastHeartbeat);
    }

    /** Immutable presence view returned to callers. */
    public static final class Presence {
        private final String workspaceId;
        private final String userId;
        private String activeFile;
        private int line;
        private int column;
        private long lastHeartbeat;

        private Presence(String workspaceId, String userId, long lastHeartbeat) {
            this(workspaceId, userId, null, 0, 0, lastHeartbeat);
        }

        private Presence(
                String workspaceId,
                String userId,
                String activeFile,
                int line,
                int column,
                long lastHeartbeat) {
            this.workspaceId = workspaceId;
            this.userId = userId;
            this.activeFile = activeFile;
            this.line = line;
            this.column = column;
            this.lastHeartbeat = lastHeartbeat;
        }

        public String workspaceId() {
            return workspaceId;
        }

        public String userId() {
            return userId;
        }

        public String activeFile() {
            return activeFile;
        }

        public int line() {
            return line;
        }

        public int column() {
            return column;
        }

        public long lastHeartbeat() {
            return lastHeartbeat;
        }
    }
}

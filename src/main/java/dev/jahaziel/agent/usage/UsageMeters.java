/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.usage;

import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Token and message counters indexed by user, workspace, and their pair. Every lookup is a single
 * HashMap lookup, and snapshots are persisted under the jar-adjacent data directory.
 */
public final class UsageMeters {
    private final AgentStore store;
    private final Map<Key, Totals> byScope = new HashMap<Key, Totals>();
    private final Map<UUID, Totals> byUser = new HashMap<UUID, Totals>();
    private final Map<UUID, Totals> byWorkspace = new HashMap<UUID, Totals>();

    public UsageMeters(AgentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Adds one usage observation and returns the updated scoped totals.
     *
     * @param tokensIn input tokens, zero or more
     * @param tokensOut output tokens, zero or more
     */
    public UsageSnapshot record(UserId user, WorkspaceId workspace, long tokensIn, long tokensOut) {
        requireOwner(user, workspace);
        if (tokensIn < 0 || tokensOut < 0) {
            throw new AgentException("token counts must not be negative");
        }
        Key key = new Key(user.value(), workspace.value());
        Totals scoped = byScope.get(key);
        if (scoped == null) {
            scoped = new Totals();
            byScope.put(key, scoped);
        }
        scoped.add(tokensIn, tokensOut);
        Totals userTotals = byUser.get(user.value());
        if (userTotals == null) {
            userTotals = new Totals();
            byUser.put(user.value(), userTotals);
        }
        userTotals.add(tokensIn, tokensOut);
        Totals workspaceTotals = byWorkspace.get(workspace.value());
        if (workspaceTotals == null) {
            workspaceTotals = new Totals();
            byWorkspace.put(workspace.value(), workspaceTotals);
        }
        workspaceTotals.add(tokensIn, tokensOut);
        UsageSnapshot snapshot =
                scoped.snapshot(user.value().toString(), workspace.value().toString());
        store.write(snapshotPath(user, workspace), snapshot);
        return snapshot;
    }

    /** Returns the totals for one user and workspace, or an all-zero snapshot. */
    public UsageSnapshot get(UserId user, WorkspaceId workspace) {
        requireOwner(user, workspace);
        Totals totals = byScope.get(new Key(user.value(), workspace.value()));
        return totals == null
                ? new UsageSnapshot(user.value().toString(), workspace.value().toString(), 0, 0, 0)
                : totals.snapshot(user.value().toString(), workspace.value().toString());
    }

    /** Returns the totals for one user across all workspaces. */
    public UsageSnapshot getByUser(UserId user) {
        Objects.requireNonNull(user, "user");
        Totals totals = byUser.get(user.value());
        return totals == null
                ? new UsageSnapshot(user.value().toString(), null, 0, 0, 0)
                : totals.snapshot(user.value().toString(), null);
    }

    /** Returns the totals for one workspace across all users. */
    public UsageSnapshot getByWorkspace(WorkspaceId workspace) {
        Objects.requireNonNull(workspace, "workspace");
        Totals totals = byWorkspace.get(workspace.value());
        return totals == null
                ? new UsageSnapshot(null, workspace.value().toString(), 0, 0, 0)
                : totals.snapshot(null, workspace.value().toString());
    }

    private Path snapshotPath(UserId user, WorkspaceId workspace) {
        return store.containedChild(
                store.usageRoot(), user.value().toString(), workspace.value().toString() + ".json");
    }

    private static void requireOwner(UserId user, WorkspaceId workspace) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(workspace, "workspace");
    }

    /** Immutable view of accumulated usage. */
    public static final class UsageSnapshot {
        private final String userId;
        private final String workspaceId;
        private final long tokensIn;
        private final long tokensOut;
        private final long messages;

        public UsageSnapshot(
                String userId, String workspaceId, long tokensIn, long tokensOut, long messages) {
            this.userId = userId;
            this.workspaceId = workspaceId;
            this.tokensIn = tokensIn;
            this.tokensOut = tokensOut;
            this.messages = messages;
        }

        public String getUserId() {
            return userId;
        }

        public String getWorkspaceId() {
            return workspaceId;
        }

        public long getTokensIn() {
            return tokensIn;
        }

        public long getTokensOut() {
            return tokensOut;
        }

        public long getMessages() {
            return messages;
        }
    }

    private static final class Totals {
        private long tokensIn;
        private long tokensOut;
        private long messages;

        void add(long input, long output) {
            tokensIn += input;
            tokensOut += output;
            messages++;
        }

        UsageSnapshot snapshot(String user, String workspace) {
            return new UsageSnapshot(user, workspace, tokensIn, tokensOut, messages);
        }
    }

    private static final class Key {
        private final UUID user;
        private final UUID workspace;
        private final int hash;

        Key(UUID user, UUID workspace) {
            this.user = user;
            this.workspace = workspace;
            this.hash = 31 * user.hashCode() + workspace.hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key that = (Key) other;
            return user.equals(that.user) && workspace.equals(that.workspace);
        }
    }
}

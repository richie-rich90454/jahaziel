/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.util.ArrayList;
import java.util.List;

/** One controller-owned live session. Observers join only with explicit consent. */
public final class LiveSession {
    private final String id;
    private final String workspaceId;
    private final String controller;
    private final String tokenHash;
    private final long createdAt;
    private final List<String> observers = new ArrayList<String>();
    private boolean active = true;

    public LiveSession(
            String id, String workspaceId, String controller, String tokenHash, long createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.controller = controller;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String controller() {
        return controller;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean active() {
        return active;
    }

    public void active(boolean active) {
        this.active = active;
    }

    public List<String> observers() {
        return new ArrayList<String>(observers);
    }

    public void addObserver(String userId) {
        if (!observers.contains(userId)) {
            observers.add(userId);
        }
    }

    public void removeObserver(String userId) {
        observers.remove(userId);
    }
}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

/** A comment anchored to a line range in one workspace file. */
public final class FileComment {
    private final String id;
    private final String workspaceId;
    private final String path;
    private final int startLine;
    private final int endLine;
    private final String author;
    private final String body;
    private final long createdAt;
    private boolean resolved;
    private long updatedAt;

    public FileComment(
            String id,
            String workspaceId,
            String path,
            int startLine,
            int endLine,
            String author,
            String body,
            long createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.path = path;
        this.startLine = startLine;
        this.endLine = endLine;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String path() {
        return path;
    }

    public int startLine() {
        return startLine;
    }

    public int endLine() {
        return endLine;
    }

    public String author() {
        return author;
    }

    public String body() {
        return body;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean resolved() {
        return resolved;
    }

    public void resolved(boolean resolved) {
        this.resolved = resolved;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void updatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}

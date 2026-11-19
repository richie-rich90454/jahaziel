/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

/** One reversible file operation. Byte payloads are kept private so they never reach JSON. */
public final class OperationRecord {
    public static final String WRITE = "WRITE";
    public static final String CREATE = "CREATE";
    public static final String DELETE = "DELETE";
    public static final String RENAME = "RENAME";
    public static final String COPY = "COPY";
    public static final String MKDIR = "MKDIR";

    private final long sequence;
    private final String id;
    private final String kind;
    private final String path;
    private final String oldPath;
    private final byte[] oldBytes;
    private final byte[] newBytes;
    private final long timestamp;
    private final String actor;

    public OperationRecord(
            long sequence,
            String id,
            String kind,
            String path,
            String oldPath,
            byte[] oldBytes,
            byte[] newBytes,
            long timestamp,
            String actor) {
        this.sequence = sequence;
        this.id = id;
        this.kind = kind;
        this.path = path;
        this.oldPath = oldPath;
        this.oldBytes = oldBytes;
        this.newBytes = newBytes;
        this.timestamp = timestamp;
        this.actor = actor == null ? "system" : actor;
    }

    public long getSequence() {
        return sequence;
    }

    public String getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getPath() {
        return path;
    }

    public String getOldPath() {
        return oldPath;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getActor() {
        return actor;
    }

    public int getOldSize() {
        return oldBytes == null ? 0 : oldBytes.length;
    }

    public int getNewSize() {
        return newBytes == null ? 0 : newBytes.length;
    }

    String kind() {
        return kind;
    }

    String oldPath() {
        return oldPath;
    }

    byte[] oldBytes() {
        return oldBytes;
    }

    byte[] newBytes() {
        return newBytes;
    }
}

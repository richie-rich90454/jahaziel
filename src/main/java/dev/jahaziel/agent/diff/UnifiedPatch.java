/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.diff;

import java.util.ArrayList;
import java.util.List;

/** A parsed, staged unified diff. Decisions are recorded here; no hunk is ever applied. */
public final class UnifiedPatch {
    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";

    private String id;
    private String userId;
    private String workspaceId;
    private String filePath;
    private String oldPath;
    private String newPath;
    private String raw;
    private List<Hunk> hunks = new ArrayList<Hunk>();
    private List<String> decisions = new ArrayList<String>();

    public UnifiedPatch() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getOldPath() {
        return oldPath;
    }

    public void setOldPath(String oldPath) {
        this.oldPath = oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public void setNewPath(String newPath) {
        this.newPath = newPath;
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }

    public List<Hunk> getHunks() {
        return hunks;
    }

    public void setHunks(List<Hunk> hunks) {
        this.hunks = hunks;
    }

    public List<String> getDecisions() {
        return decisions;
    }

    public void setDecisions(List<String> decisions) {
        this.decisions = decisions;
    }

    /** One {@code @@} section of a unified diff. */
    public static final class Hunk {
        private int oldStart;
        private int oldCount;
        private int newStart;
        private int newCount;
        private List<String> lines = new ArrayList<String>();

        public Hunk() {}

        public int getOldStart() {
            return oldStart;
        }

        public void setOldStart(int oldStart) {
            this.oldStart = oldStart;
        }

        public int getOldCount() {
            return oldCount;
        }

        public void setOldCount(int oldCount) {
            this.oldCount = oldCount;
        }

        public int getNewStart() {
            return newStart;
        }

        public void setNewStart(int newStart) {
            this.newStart = newStart;
        }

        public int getNewCount() {
            return newCount;
        }

        public void setNewCount(int newCount) {
            this.newCount = newCount;
        }

        public List<String> getLines() {
            return lines;
        }

        public void setLines(List<String> lines) {
            this.lines = lines;
        }
    }
}

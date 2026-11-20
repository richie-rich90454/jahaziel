/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

import java.util.List;
import java.util.Objects;

/** Fixed, workspace-scoped Git operations for the browser workbench. */
public abstract class WorkspaceGitService {
    public abstract Status status(WorkspaceId workspaceId);

    public abstract Diff diff(WorkspaceId workspaceId, String path);

    public abstract Status stage(WorkspaceId workspaceId, String path);

    public abstract Status unstage(WorkspaceId workspaceId, String path);

    public abstract CommitResult commit(WorkspaceId workspaceId, String message);

    public static final class Status {
        private final boolean repository;
        private final String branch;
        private final List<Change> changes;

        public Status(boolean repository, String branch, List<Change> changes) {
            this.repository = repository;
            this.branch = branch;
            this.changes = changes;
        }

        public boolean repository() {
            return repository;
        }

        public boolean getRepository() {
            return repository;
        }

        public boolean isRepository() {
            return repository;
        }

        public String branch() {
            return branch;
        }

        public String getBranch() {
            return branch;
        }

        public List<Change> changes() {
            return changes;
        }

        public List<Change> getChanges() {
            return changes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Status other = (Status) o;
            return repository == other.repository
                    && Objects.equals(branch, other.branch)
                    && Objects.equals(changes, other.changes);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + (repository ? 1231 : 1237);
            result = 31 * result + Objects.hashCode(branch);
            result = 31 * result + Objects.hashCode(changes);
            return result;
        }

        @Override
        public String toString() {
            return "Status{"
                    + "repository="
                    + repository
                    + ", branch="
                    + branch
                    + ", changes="
                    + changes
                    + '}';
        }
    }

    public static final class Change {
        private final String path;
        private final String indexStatus;
        private final String worktreeStatus;

        public Change(String path, String indexStatus, String worktreeStatus) {
            this.path = path;
            this.indexStatus = indexStatus;
            this.worktreeStatus = worktreeStatus;
        }

        public String path() {
            return path;
        }

        public String getPath() {
            return path;
        }

        public String indexStatus() {
            return indexStatus;
        }

        public String getIndexStatus() {
            return indexStatus;
        }

        public String worktreeStatus() {
            return worktreeStatus;
        }

        public String getWorktreeStatus() {
            return worktreeStatus;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Change other = (Change) o;
            return Objects.equals(path, other.path)
                    && Objects.equals(indexStatus, other.indexStatus)
                    && Objects.equals(worktreeStatus, other.worktreeStatus);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(path);
            result = 31 * result + Objects.hashCode(indexStatus);
            result = 31 * result + Objects.hashCode(worktreeStatus);
            return result;
        }

        @Override
        public String toString() {
            return "Change{"
                    + "path="
                    + path
                    + ", indexStatus="
                    + indexStatus
                    + ", worktreeStatus="
                    + worktreeStatus
                    + '}';
        }
    }

    public static final class Diff {
        private final String path;
        private final String content;

        public Diff(String path, String content) {
            this.path = path;
            this.content = content;
        }

        public String path() {
            return path;
        }

        public String getPath() {
            return path;
        }

        public String content() {
            return content;
        }

        public String getContent() {
            return content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Diff other = (Diff) o;
            return Objects.equals(path, other.path) && Objects.equals(content, other.content);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(path);
            result = 31 * result + Objects.hashCode(content);
            return result;
        }

        @Override
        public String toString() {
            return "Diff{" + "path=" + path + ", content=" + content + '}';
        }
    }

    public static final class CommitResult {
        private final String commit;
        private final String summary;

        public CommitResult(String commit, String summary) {
            this.commit = commit;
            this.summary = summary;
        }

        public String commit() {
            return commit;
        }

        public String getCommit() {
            return commit;
        }

        public String summary() {
            return summary;
        }

        public String getSummary() {
            return summary;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CommitResult other = (CommitResult) o;
            return Objects.equals(commit, other.commit) && Objects.equals(summary, other.summary);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(commit);
            result = 31 * result + Objects.hashCode(summary);
            return result;
        }

        @Override
        public String toString() {
            return "CommitResult{" + "commit=" + commit + ", summary=" + summary + '}';
        }
    }
}

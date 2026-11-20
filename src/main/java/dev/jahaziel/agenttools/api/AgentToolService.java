/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agenttools.api;

import dev.jahaziel.identity.domain.UserId;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Per-user managed coding-agent tools. Credentials and writable profiles stay user-scoped. */
public abstract class AgentToolService {
    /** Returns the server-owned catalog annotated with this user's install state. */
    public abstract List<ToolStatus> catalog(UserId userId);

    /** Starts or returns the current OpenCode installation operation for this user. */
    public abstract ToolStatus installOpenCode(UserId userId);

    /** Explicitly reinstalls the catalog's pinned OpenCode version for this user. */
    public abstract ToolStatus updateOpenCode(UserId userId);

    /** Removes the managed OpenCode executable while preserving the user's configuration. */
    public abstract ToolStatus removeOpenCode(UserId userId);

    /** Returns the managed executable directory added to this user's terminal PATH. */
    public abstract Path executableDirectory(UserId userId);

    /** Returns the user-scoped configuration, data, and cache root for CLI sessions. */
    public abstract Path profileDirectory(UserId userId);

    /** Creates the isolated user tool and CLI profile directories with owner-only permissions. */
    public abstract void prepareProfile(UserId userId);

    /** One catalog entry and the current user's install status. */
    public static final class ToolStatus {
        private final String id;
        private final String name;
        private final String version;
        private final String architecture;
        private final String status;
        private final long downloadSizeBytes;
        private final long installedSizeBytes;
        private final int progressPercent;
        private final String error;

        /** Creates a catalog entry. */
        public ToolStatus(
                String id,
                String name,
                String version,
                String architecture,
                String status,
                long downloadSizeBytes,
                long installedSizeBytes,
                int progressPercent,
                String error) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.architecture = architecture;
            this.status = status;
            this.downloadSizeBytes = downloadSizeBytes;
            this.installedSizeBytes = installedSizeBytes;
            this.progressPercent = progressPercent;
            this.error = error;
        }

        /** Returns the tool identifier. */
        public String id() {
            return id;
        }

        public String getId() {
            return id;
        }

        /** Returns the display name. */
        public String name() {
            return name;
        }

        public String getName() {
            return name;
        }

        /** Returns the pinned version. */
        public String version() {
            return version;
        }

        public String getVersion() {
            return version;
        }

        /** Returns the host architecture. */
        public String architecture() {
            return architecture;
        }

        public String getArchitecture() {
            return architecture;
        }

        /** Returns the install status. */
        public String status() {
            return status;
        }

        public String getStatus() {
            return status;
        }

        /** Returns the download size in bytes. */
        public long downloadSizeBytes() {
            return downloadSizeBytes;
        }

        public long getDownloadSizeBytes() {
            return downloadSizeBytes;
        }

        /** Returns the installed size in bytes. */
        public long installedSizeBytes() {
            return installedSizeBytes;
        }

        public long getInstalledSizeBytes() {
            return installedSizeBytes;
        }

        /** Returns the install progress percentage. */
        public int progressPercent() {
            return progressPercent;
        }

        public int getProgressPercent() {
            return progressPercent;
        }

        /** Returns the error detail, or an empty string. */
        public String error() {
            return error;
        }

        public String getError() {
            return error;
        }

        /** Compares by class and every component. */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ToolStatus other = (ToolStatus) o;
            return downloadSizeBytes == other.downloadSizeBytes
                    && installedSizeBytes == other.installedSizeBytes
                    && progressPercent == other.progressPercent
                    && Objects.equals(id, other.id)
                    && Objects.equals(name, other.name)
                    && Objects.equals(version, other.version)
                    && Objects.equals(architecture, other.architecture)
                    && Objects.equals(status, other.status)
                    && Objects.equals(error, other.error);
        }

        /** Returns a hash derived from every component. */
        @Override
        public int hashCode() {
            return Objects.hash(
                    id,
                    name,
                    version,
                    architecture,
                    status,
                    downloadSizeBytes,
                    installedSizeBytes,
                    progressPercent,
                    error);
        }

        /** Returns a readable representation. */
        @Override
        public String toString() {
            return "ToolStatus[id="
                    + id
                    + ", name="
                    + name
                    + ", version="
                    + version
                    + ", architecture="
                    + architecture
                    + ", status="
                    + status
                    + ", downloadSizeBytes="
                    + downloadSizeBytes
                    + ", installedSizeBytes="
                    + installedSizeBytes
                    + ", progressPercent="
                    + progressPercent
                    + ", error="
                    + error
                    + "]";
        }
    }
}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared, opt-in language-tool cache and workspace selections. */
public abstract class LanguagePackService {
    /** Returns the pinned server-owned pack catalog for the current host. */
    public abstract List<Pack> catalog();

    /** Returns catalog entries annotated with this workspace's selected state. */
    public abstract List<Pack> forWorkspace(WorkspaceId workspaceId);

    /** Installs a catalog pack if needed and selects it for the workspace. */
    public abstract Pack select(WorkspaceId workspaceId, String languageId);

    /** Removes a workspace selection without deleting the shared cached tool. */
    public abstract void deselect(WorkspaceId workspaceId, String languageId);

    /** Clears all selected-pack references after a workspace is deleted. */
    public abstract void clearWorkspace(WorkspaceId workspaceId);

    /** Resolves an installed, selected launch artifact for an authorized language session. */
    public abstract Path artifactFor(WorkspaceId workspaceId, String languageId);

    /** Reports whether this server has a fixed, server-owned launch contract for this language. */
    public abstract boolean supportsLanguageService(String languageId);

    /** Builds the fixed server-owned launch command for an authorized language-service session. */
    public abstract List<String> launchCommandFor(
            WorkspaceId workspaceId, String languageId, Path stateDirectory);

    /** Returns a fixed server-owned environment for a language service. */
    public Map<String, String> launchEnvironmentFor(
            WorkspaceId workspaceId, String languageId, Path workspaceHome, Path stateDirectory) {
        return Map.of();
    }

    /** Returns server-owned initialization options; clients cannot select executable paths. */
    public Map<String, Object> initializationOptionsFor(
            WorkspaceId workspaceId, String languageId) {
        return Map.of();
    }

    /** Reports cache usage without changing files. */
    public abstract CacheReport report();

    /** Lists reclaimable versions, or removes them when dryRun is false. */
    public abstract PruneReport prune(boolean dryRun);

    /** One immutable, pinned language-pack release. */
    public static final class Pack {
        private final String id;
        private final String name;
        private final String version;
        private final long downloadBytes;
        private final String sha256;
        private final List<String> checksums;
        private final List<String> supportedArchitectures;
        private final boolean installed;
        private final boolean selected;
        private final String availability;
        private final boolean supportedByTemplate;
        private final String installationNote;

        /**
         * Creates a pack view.
         *
         * @param id the language id
         * @param name the display name
         * @param version the pinned version
         * @param downloadBytes the archive size
         * @param sha256 the archive digest
         * @param checksums the display checksums
         * @param supportedArchitectures the supported host architectures
         * @param installed whether the pack is installed
         * @param selected whether the pack is selected for the workspace
         * @param availability the availability note
         * @param supportedByTemplate whether the workspace template supports the language
         * @param installationNote the installation note
         */
        public Pack(
                String id,
                String name,
                String version,
                long downloadBytes,
                String sha256,
                List<String> checksums,
                List<String> supportedArchitectures,
                boolean installed,
                boolean selected,
                String availability,
                boolean supportedByTemplate,
                String installationNote) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.downloadBytes = downloadBytes;
            this.sha256 = sha256;
            this.checksums = checksums;
            this.supportedArchitectures = supportedArchitectures;
            this.installed = installed;
            this.selected = selected;
            this.availability = availability;
            this.supportedByTemplate = supportedByTemplate;
            this.installationNote = installationNote;
        }

        /** Returns the language id. */
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

        /** Returns the archive size. */
        public long downloadBytes() {
            return downloadBytes;
        }

        public long getDownloadBytes() {
            return downloadBytes;
        }

        /** Returns the archive digest. */
        public String sha256() {
            return sha256;
        }

        public String getSha256() {
            return sha256;
        }

        /** Returns the display checksums. */
        public List<String> checksums() {
            return checksums;
        }

        public List<String> getChecksums() {
            return checksums;
        }

        /** Returns the supported host architectures. */
        public List<String> supportedArchitectures() {
            return supportedArchitectures;
        }

        public List<String> getSupportedArchitectures() {
            return supportedArchitectures;
        }

        /** Returns whether the pack is installed. */
        public boolean installed() {
            return installed;
        }

        public boolean getInstalled() {
            return installed;
        }

        public boolean isInstalled() {
            return installed;
        }

        /** Returns whether the pack is selected for the workspace. */
        public boolean selected() {
            return selected;
        }

        public boolean getSelected() {
            return selected;
        }

        public boolean isSelected() {
            return selected;
        }

        /** Returns the availability note. */
        public String availability() {
            return availability;
        }

        public String getAvailability() {
            return availability;
        }

        /** Returns whether the workspace template supports the language. */
        public boolean supportedByTemplate() {
            return supportedByTemplate;
        }

        public boolean getSupportedByTemplate() {
            return supportedByTemplate;
        }

        public boolean isSupportedByTemplate() {
            return supportedByTemplate;
        }

        /** Returns the installation note. */
        public String installationNote() {
            return installationNote;
        }

        public String getInstallationNote() {
            return installationNote;
        }

        /** Returns this pack with its availability for the workspace template attached. */
        public Pack withTemplateSupport(boolean supported) {
            return new Pack(
                    id,
                    name,
                    version,
                    downloadBytes,
                    sha256,
                    checksums,
                    supportedArchitectures,
                    installed,
                    selected,
                    availability,
                    supported,
                    installationNote);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Pack)) {
                return false;
            }
            Pack that = (Pack) other;
            return downloadBytes == that.downloadBytes
                    && installed == that.installed
                    && selected == that.selected
                    && supportedByTemplate == that.supportedByTemplate
                    && id.equals(that.id)
                    && name.equals(that.name)
                    && version.equals(that.version)
                    && sha256.equals(that.sha256)
                    && checksums.equals(that.checksums)
                    && supportedArchitectures.equals(that.supportedArchitectures)
                    && availability.equals(that.availability)
                    && installationNote.equals(that.installationNote);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    id,
                    name,
                    version,
                    downloadBytes,
                    sha256,
                    checksums,
                    supportedArchitectures,
                    installed,
                    selected,
                    availability,
                    supportedByTemplate,
                    installationNote);
        }

        @Override
        public String toString() {
            return "Pack[id="
                    + id
                    + ", name="
                    + name
                    + ", version="
                    + version
                    + ", downloadBytes="
                    + downloadBytes
                    + ", sha256="
                    + sha256
                    + ", checksums="
                    + checksums
                    + ", supportedArchitectures="
                    + supportedArchitectures
                    + ", installed="
                    + installed
                    + ", selected="
                    + selected
                    + ", availability="
                    + availability
                    + ", supportedByTemplate="
                    + supportedByTemplate
                    + ", installationNote="
                    + installationNote
                    + "]";
        }
    }

    /** Current cache usage. */
    public static final class CacheReport {
        private final long bytes;
        private final List<Pack> packs;

        /**
         * Creates a cache report.
         *
         * @param bytes the cache size in bytes
         * @param packs the cached packs
         */
        public CacheReport(long bytes, List<Pack> packs) {
            this.bytes = bytes;
            this.packs = packs;
        }

        /** Returns the cache size in bytes. */
        public long bytes() {
            return bytes;
        }

        public long getBytes() {
            return bytes;
        }

        /** Returns the cached packs. */
        public List<Pack> packs() {
            return packs;
        }

        public List<Pack> getPacks() {
            return packs;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof CacheReport
                    && bytes == ((CacheReport) other).bytes
                    && packs.equals(((CacheReport) other).packs);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(bytes) + packs.hashCode();
        }

        @Override
        public String toString() {
            return "CacheReport[bytes=" + bytes + ", packs=" + packs + "]";
        }
    }

    /** Dry-run or completed prune result. */
    public static final class PruneReport {
        private final boolean dryRun;
        private final long bytesReclaimed;
        private final List<String> versions;

        /**
         * Creates a prune report.
         *
         * @param dryRun whether the prune was a dry run
         * @param bytesReclaimed the reclaimed bytes
         * @param versions the reclaimed versions
         */
        public PruneReport(boolean dryRun, long bytesReclaimed, List<String> versions) {
            this.dryRun = dryRun;
            this.bytesReclaimed = bytesReclaimed;
            this.versions = versions;
        }

        /** Returns whether the prune was a dry run. */
        public boolean dryRun() {
            return dryRun;
        }

        public boolean getDryRun() {
            return dryRun;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        /** Returns the reclaimed bytes. */
        public long bytesReclaimed() {
            return bytesReclaimed;
        }

        public long getBytesReclaimed() {
            return bytesReclaimed;
        }

        /** Returns the reclaimed versions. */
        public List<String> versions() {
            return versions;
        }

        public List<String> getVersions() {
            return versions;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PruneReport)) {
                return false;
            }
            PruneReport that = (PruneReport) other;
            return dryRun == that.dryRun
                    && bytesReclaimed == that.bytesReclaimed
                    && versions.equals(that.versions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dryRun, bytesReclaimed, versions);
        }

        @Override
        public String toString() {
            return "PruneReport[dryRun="
                    + dryRun
                    + ", bytesReclaimed="
                    + bytesReclaimed
                    + ", versions="
                    + versions
                    + "]";
        }
    }
}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.domain;

import dev.jahaziel.workspace.domain.WorkspaceId;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Metadata of a point-in-time snapshot of a workspace home folder.
 *
 * @param id the snapshot identifier
 * @param workspaceId the workspace
 * @param tag the human tag, unique per workspace
 * @param kind how the snapshot was triggered
 * @param createdAt when the snapshot was taken
 * @param createdBy who triggered it, user id, system, or admin id
 * @param snapshotPath absolute path to the read-only snapshot folder
 * @param logicalBytes logical size in bytes
 * @param physicalBytes physical size in bytes
 * @param parentId the previous snapshot, empty for the first
 * @param manifestSha256 SHA-256 of the snapshot manifest, empty until verified
 */
public final class SnapshotMetadata {
    private final SnapshotId id;
    private final WorkspaceId workspaceId;
    private final String tag;
    private final SnapshotKind kind;
    private final Instant createdAt;
    private final String createdBy;
    private final Path snapshotPath;
    private final long logicalBytes;
    private final long physicalBytes;
    private final Optional<SnapshotId> parentId;
    private final String manifestSha256;

    /**
     * Creates the metadata.
     *
     * @param id the snapshot identifier
     * @param workspaceId the workspace
     * @param tag the human tag, unique per workspace
     * @param kind how the snapshot was triggered
     * @param createdAt when the snapshot was taken
     * @param createdBy who triggered it, user id, system, or admin id
     * @param snapshotPath absolute path to the read-only snapshot folder
     * @param logicalBytes logical size in bytes
     * @param physicalBytes physical size in bytes
     * @param parentId the previous snapshot, empty for the first
     * @param manifestSha256 SHA-256 of the snapshot manifest, empty until verified
     */
    public SnapshotMetadata(
            SnapshotId id,
            WorkspaceId workspaceId,
            String tag,
            SnapshotKind kind,
            Instant createdAt,
            String createdBy,
            Path snapshotPath,
            long logicalBytes,
            long physicalBytes,
            Optional<SnapshotId> parentId,
            String manifestSha256) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(createdAt, "createdAt");
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
        Objects.requireNonNull(snapshotPath, "snapshotPath");
        if (logicalBytes < 0) {
            throw new IllegalArgumentException("logicalBytes must not be negative");
        }
        if (physicalBytes < 0) {
            throw new IllegalArgumentException("physicalBytes must not be negative");
        }
        Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(manifestSha256, "manifestSha256");
        this.id = id;
        this.workspaceId = workspaceId;
        this.tag = tag;
        this.kind = kind;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.snapshotPath = snapshotPath;
        this.logicalBytes = logicalBytes;
        this.physicalBytes = physicalBytes;
        this.parentId = parentId;
        this.manifestSha256 = manifestSha256;
    }

    /** Returns the snapshot identifier. */
    public SnapshotId id() {
        return id;
    }

    public SnapshotId getId() {
        return id;
    }

    /** Returns the workspace. */
    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    public WorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    /** Returns the human tag. */
    public String tag() {
        return tag;
    }

    public String getTag() {
        return tag;
    }

    /** Returns how the snapshot was triggered. */
    public SnapshotKind kind() {
        return kind;
    }

    public SnapshotKind getKind() {
        return kind;
    }

    /** Returns when the snapshot was taken. */
    public Instant createdAt() {
        return createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Returns who triggered the snapshot. */
    public String createdBy() {
        return createdBy;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    /** Returns the absolute path to the read-only snapshot folder. */
    public Path snapshotPath() {
        return snapshotPath;
    }

    public Path getSnapshotPath() {
        return snapshotPath;
    }

    /** Returns the logical size in bytes. */
    public long logicalBytes() {
        return logicalBytes;
    }

    public long getLogicalBytes() {
        return logicalBytes;
    }

    /** Returns the physical size in bytes. */
    public long physicalBytes() {
        return physicalBytes;
    }

    public long getPhysicalBytes() {
        return physicalBytes;
    }

    /** Returns the previous snapshot, empty for the first. */
    public Optional<SnapshotId> parentId() {
        return parentId;
    }

    public Optional<SnapshotId> getParentId() {
        return parentId;
    }

    /** Returns the manifest digest. */
    public String manifestSha256() {
        return manifestSha256;
    }

    public String getManifestSha256() {
        return manifestSha256;
    }

    /**
     * Creates manual snapshot metadata with a fresh identifier.
     *
     * @param workspaceId the workspace
     * @param tag the human tag
     * @param createdBy who triggered it
     * @param snapshotPath absolute path to the read-only snapshot folder
     * @return the metadata
     */
    public static SnapshotMetadata manual(
            WorkspaceId workspaceId, String tag, String createdBy, Path snapshotPath) {
        return new SnapshotMetadata(
                new SnapshotId(UUID.randomUUID()),
                workspaceId,
                tag,
                SnapshotKind.MANUAL,
                Instant.now(),
                createdBy,
                snapshotPath,
                0,
                0,
                Optional.empty(),
                "pending");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SnapshotMetadata)) {
            return false;
        }
        SnapshotMetadata that = (SnapshotMetadata) other;
        return logicalBytes == that.logicalBytes
                && physicalBytes == that.physicalBytes
                && id.equals(that.id)
                && workspaceId.equals(that.workspaceId)
                && tag.equals(that.tag)
                && kind.equals(that.kind)
                && createdAt.equals(that.createdAt)
                && createdBy.equals(that.createdBy)
                && snapshotPath.equals(that.snapshotPath)
                && parentId.equals(that.parentId)
                && manifestSha256.equals(that.manifestSha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                workspaceId,
                tag,
                kind,
                createdAt,
                createdBy,
                snapshotPath,
                logicalBytes,
                physicalBytes,
                parentId,
                manifestSha256);
    }

    @Override
    public String toString() {
        return "SnapshotMetadata[id="
                + id
                + ", workspaceId="
                + workspaceId
                + ", tag="
                + tag
                + ", kind="
                + kind
                + ", createdAt="
                + createdAt
                + ", createdBy="
                + createdBy
                + ", snapshotPath="
                + snapshotPath
                + ", logicalBytes="
                + logicalBytes
                + ", physicalBytes="
                + physicalBytes
                + ", parentId="
                + parentId
                + ", manifestSha256="
                + manifestSha256
                + "]";
    }
}

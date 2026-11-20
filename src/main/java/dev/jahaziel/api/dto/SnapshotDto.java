/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.snapshot.api.SnapshotKinds;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;

import java.time.Instant;
import java.util.UUID;

/**
 * A snapshot for reads.
 *
 * @param snapshotId the snapshot identifier
 * @param tag the human tag
 * @param kind the stored kind
 * @param createdAt when the snapshot was taken
 * @param createdBy who triggered it
 * @param logicalBytes logical size in bytes
 * @param physicalBytes physical size in bytes
 */
public final class SnapshotDto {

    private final UUID snapshotId;

    private final String tag;

    private final String kind;

    private final Instant createdAt;

    private final String createdBy;

    private final long logicalBytes;

    private final long physicalBytes;

    public SnapshotDto(
            UUID snapshotId,
            String tag,
            String kind,
            Instant createdAt,
            String createdBy,
            long logicalBytes,
            long physicalBytes) {
        this.snapshotId = snapshotId;
        this.tag = tag;
        this.kind = kind;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.logicalBytes = logicalBytes;
        this.physicalBytes = physicalBytes;
    }

    /**
     * Maps metadata to a DTO.
     *
     * @param metadata the metadata
     * @return the DTO
     */
    public static SnapshotDto from(SnapshotMetadata metadata) {
        return new SnapshotDto(
                metadata.id().value(),
                metadata.tag(),
                SnapshotKinds.toString(metadata.kind()),
                metadata.createdAt(),
                metadata.createdBy(),
                metadata.logicalBytes(),
                metadata.physicalBytes());
    }

    public UUID snapshotId() {
        return this.snapshotId;
    }

    public UUID getSnapshotId() {
        return this.snapshotId;
    }

    public String tag() {
        return this.tag;
    }

    public String getTag() {
        return this.tag;
    }

    public String kind() {
        return this.kind;
    }

    public String getKind() {
        return this.kind;
    }

    public Instant createdAt() {
        return this.createdAt;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public String createdBy() {
        return this.createdBy;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public long logicalBytes() {
        return this.logicalBytes;
    }

    public long getLogicalBytes() {
        return this.logicalBytes;
    }

    public long physicalBytes() {
        return this.physicalBytes;
    }

    public long getPhysicalBytes() {
        return this.physicalBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SnapshotDto)) return false;
        SnapshotDto other = (SnapshotDto) o;
        return (java.util.Objects.equals(this.snapshotId, other.snapshotId))
                && java.util.Objects.equals(this.tag, other.tag)
                && java.util.Objects.equals(this.kind, other.kind)
                && java.util.Objects.equals(this.createdAt, other.createdAt)
                && java.util.Objects.equals(this.createdBy, other.createdBy)
                && this.logicalBytes == other.logicalBytes
                && this.physicalBytes == other.physicalBytes;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.snapshotId,
                this.tag,
                this.kind,
                this.createdAt,
                this.createdBy,
                this.logicalBytes,
                this.physicalBytes);
    }

    @Override
    public String toString() {
        return "SnapshotDto["
                + "snapshotId="
                + this.snapshotId
                + ", "
                + "tag="
                + this.tag
                + ", "
                + "kind="
                + this.kind
                + ", "
                + "createdAt="
                + this.createdAt
                + ", "
                + "createdBy="
                + this.createdBy
                + ", "
                + "logicalBytes="
                + this.logicalBytes
                + ", "
                + "physicalBytes="
                + this.physicalBytes
                + "]";
    }
}

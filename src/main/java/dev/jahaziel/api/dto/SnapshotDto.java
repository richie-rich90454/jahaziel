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
public record SnapshotDto(
    UUID snapshotId,
    String tag,
    String kind,
    Instant createdAt,
    String createdBy,
    long logicalBytes,
    long physicalBytes) {
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
}

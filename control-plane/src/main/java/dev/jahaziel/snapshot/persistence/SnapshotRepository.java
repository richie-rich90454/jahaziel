/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for SnapshotEntity persistence. */
public interface SnapshotRepository extends JpaRepository<SnapshotEntity, UUID> {}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.storage.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for CacheArtifactEntity persistence. */
public interface CacheArtifactRepository extends JpaRepository<CacheArtifactEntity, UUID> {}

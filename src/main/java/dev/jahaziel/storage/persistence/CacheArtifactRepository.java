/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.storage.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data repository for CacheArtifactEntity; interface is required by Spring Data. */
public interface CacheArtifactRepository extends JpaRepository<CacheArtifactEntity, UUID> {}

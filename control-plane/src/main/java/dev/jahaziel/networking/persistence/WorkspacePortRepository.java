/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for WorkspacePortEntity persistence. */
public interface WorkspacePortRepository
    extends JpaRepository<WorkspacePortEntity, WorkspacePortEntity.Id> {}

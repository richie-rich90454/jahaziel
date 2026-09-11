/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for WorkspaceAclEntity persistence. */
public interface WorkspaceAclRepository
    extends JpaRepository<WorkspaceAclEntity, WorkspaceAclEntity.Id> {}

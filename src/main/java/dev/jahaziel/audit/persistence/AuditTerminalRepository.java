/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for AuditTerminalEntity; interface is required by Spring Data. */
public interface AuditTerminalRepository
        extends JpaRepository<AuditTerminalEntity, AuditTerminalEntity.Id> {}

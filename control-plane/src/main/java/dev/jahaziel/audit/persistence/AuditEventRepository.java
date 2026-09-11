/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for AuditEventEntity persistence. */
public interface AuditEventRepository
    extends JpaRepository<AuditEventEntity, AuditEventEntity.Id> {}

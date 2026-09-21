/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for AuditProcessEntity persistence. */
public interface AuditProcessRepository
    extends JpaRepository<AuditProcessEntity, AuditProcessEntity.Id> {}

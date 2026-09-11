/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.administration.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for AdminActionEntity persistence. */
public interface AdminActionRepository extends JpaRepository<AdminActionEntity, UUID> {}

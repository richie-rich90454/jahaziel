/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for PolicyEntity persistence. */
public interface PolicyRepository extends JpaRepository<PolicyEntity, UUID> {}

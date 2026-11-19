/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Repository for PolicyEntity persistence. */
public interface PolicyRepository extends JpaRepository<PolicyEntity, UUID> {}

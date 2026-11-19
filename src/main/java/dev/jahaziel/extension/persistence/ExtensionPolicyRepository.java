/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Repository for ExtensionPolicyEntity persistence. */
public interface ExtensionPolicyRepository extends JpaRepository<ExtensionPolicyEntity, UUID> {}

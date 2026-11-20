/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data repository for ExtensionPolicyEntity; interface is required by Spring Data. */
public interface ExtensionPolicyRepository extends JpaRepository<ExtensionPolicyEntity, UUID> {}

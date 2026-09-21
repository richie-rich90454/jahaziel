/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for ExtensionPolicyEntity persistence. */
public interface ExtensionPolicyRepository extends JpaRepository<ExtensionPolicyEntity, UUID> {}

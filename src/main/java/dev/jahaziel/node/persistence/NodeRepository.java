/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for NodeEntity; interface is required by Spring Data. */
public interface NodeRepository extends JpaRepository<NodeEntity, String> {}

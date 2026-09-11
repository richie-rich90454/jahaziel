/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for ResourceMetricEntity persistence. */
public interface ResourceMetricRepository
    extends JpaRepository<ResourceMetricEntity, ResourceMetricEntity.Id> {}

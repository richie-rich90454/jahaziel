/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for UserGroupEntity persistence. */
public interface UserGroupRepository extends JpaRepository<UserGroupEntity, UserGroupEntity.Id> {}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for UserRoleEntity persistence. */
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Id> {}

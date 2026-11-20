/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for UserRoleEntity; interface is required by Spring Data. */
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Id> {
    /**
     * Finds all role grants of a user.
     *
     * @param userId the user identifier
     * @return the grants
     */
    List<UserRoleEntity> findByIdUserId(UUID userId);
}

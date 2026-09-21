/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for UserRoleEntity persistence. */
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Id> {
  /**
   * Finds all role grants of a user.
   *
   * @param userId the user identifier
   * @return the grants
   */
  List<UserRoleEntity> findByIdUserId(UUID userId);
}

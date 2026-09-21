/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for UserGroupEntity persistence. */
public interface UserGroupRepository extends JpaRepository<UserGroupEntity, UserGroupEntity.Id> {
  /**
   * Finds all group memberships of a user.
   *
   * @param userId the user identifier
   * @return the memberships
   */
  List<UserGroupEntity> findByIdUserId(UUID userId);
}

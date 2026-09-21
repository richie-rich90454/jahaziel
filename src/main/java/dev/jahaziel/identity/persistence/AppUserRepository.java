/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for AppUserEntity persistence. */
public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {
  /**
   * Finds the account linked to an identity provider subject.
   *
   * @param externalId the OIDC subject
   * @return the account, when linked
   */
  Optional<AppUserEntity> findByExternalId(String externalId);

  /**
   * Finds accounts by email, case-insensitively, for the one-time legacy join.
   *
   * @param email the email address
   * @return the matching accounts
   */
  List<AppUserEntity> findByEmailIgnoreCase(String email);
}

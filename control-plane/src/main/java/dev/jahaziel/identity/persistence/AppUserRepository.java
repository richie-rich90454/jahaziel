/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for AppUserEntity persistence. */
public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {}

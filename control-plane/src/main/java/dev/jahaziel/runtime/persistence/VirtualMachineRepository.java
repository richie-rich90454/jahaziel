/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for VirtualMachineEntity persistence. */
public interface VirtualMachineRepository extends JpaRepository<VirtualMachineEntity, UUID> {
  /**
   * Finds the machine of a workspace.
   *
   * @param workspaceId the workspace identifier
   * @return the machine, empty when none exists
   */
  Optional<VirtualMachineEntity> findByWorkspaceId(UUID workspaceId);
}

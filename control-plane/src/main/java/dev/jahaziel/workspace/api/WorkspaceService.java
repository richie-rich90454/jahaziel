/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.List;

/** Workspaces: create, read, describe, delete. Lifecycle arrives with the runtime slice. */
public interface WorkspaceService {
  /**
   * Creates a workspace. The node must already be registered; memory and disk are reserved at
   * start, not at creation.
   *
   * @param owner the owning user
   * @param name the workspace name, unique per owner
   * @param vcpuCount virtual CPU count
   * @param memoryMib guest memory in MiB
   * @param nodeId the node hosting the workspace
   * @return the created workspace in requested state
   * @throws dev.jahaziel.identity.api.UserNotFoundException when the owner does not exist
   */
  Workspace create(UserId owner, String name, int vcpuCount, long memoryMib, NodeId nodeId);

  /**
   * Returns one workspace.
   *
   * @param id the workspace identifier
   * @return the workspace
   * @throws WorkspaceNotFoundException when the workspace does not exist
   */
  Workspace get(WorkspaceId id);

  /**
   * Describes one workspace for reads. Usage stays zero until the metrics slice supplies live
   * values.
   *
   * @param id the workspace identifier
   * @return the view
   * @throws WorkspaceNotFoundException when the workspace does not exist
   */
  WorkspaceView describe(WorkspaceId id);

  /**
   * Describes all workspaces of an owner.
   *
   * @param owner the owning user
   * @return the views, empty when none exist
   */
  List<WorkspaceView> describeByOwner(UserId owner);

  /**
   * Destroys a workspace and removes its rows. Snapshots cascade with the workspace row.
   *
   * @param id the workspace identifier
   * @throws WorkspaceNotFoundException when the workspace does not exist
   */
  void delete(WorkspaceId id);
}

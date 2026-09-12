/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.runtime.domain.VirtualMachine;
import dev.jahaziel.snapshot.domain.SnapshotId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One isolated Linux development environment of a user. Enforces its own lifecycle. */
public final class Workspace {
  private final WorkspaceId id;
  private final UserId owner;
  private final String name;
  private WorkspaceState state;
  private WorkspaceQuota quota;
  private final VirtualMachine vm;
  private AccessControlList acl;
  private ResourcePolicy policy;
  private final SnapshotHistory snapshots = new SnapshotHistory();

  /**
   * Creates a requested workspace.
   *
   * @param id the workspace identifier
   * @param owner the owning user
   * @param name the workspace name, unique per owner
   * @param quota the logical quota
   * @param vm the virtual machine
   */
  public Workspace(
      WorkspaceId id, UserId owner, String name, WorkspaceQuota quota, VirtualMachine vm) {
    this.id = Objects.requireNonNull(id, "id");
    this.owner = Objects.requireNonNull(owner, "owner");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (name.length() > 64) {
      throw new IllegalArgumentException("name must be at most 64 characters");
    }
    this.name = name;
    this.quota = Objects.requireNonNull(quota, "quota");
    this.vm = Objects.requireNonNull(vm, "vm");
    this.state = new WorkspaceState.Requested(Instant.now());
    this.acl = AccessControlList.empty();
    this.policy = ResourcePolicy.defaults();
  }

  /** Returns the workspace identifier. */
  public WorkspaceId id() {
    return id;
  }

  /** Returns the owning user. */
  public UserId owner() {
    return owner;
  }

  /** Returns the workspace name. */
  public String name() {
    return name;
  }

  /** Returns the current state. */
  public WorkspaceState state() {
    return state;
  }

  /** Returns the logical quota. */
  public WorkspaceQuota quota() {
    return quota;
  }

  /** Returns the virtual machine. */
  public VirtualMachine vm() {
    return vm;
  }

  /** Returns the access control list. */
  public AccessControlList acl() {
    return acl;
  }

  /** Returns the resource policy. */
  public ResourcePolicy policy() {
    return policy;
  }

  /**
   * Returns the recorded snapshots.
   *
   * @return a defensive copy
   */
  public List<SnapshotId> snapshots() {
    return snapshots.snapshots();
  }

  /**
   * Checks ownership.
   *
   * @param candidate the candidate user
   * @return true when the candidate owns the workspace
   */
  public boolean isOwnedBy(UserId candidate) {
    return owner.equals(candidate);
  }

  /** Returns true while serving a user session. */
  public boolean isActive() {
    return state instanceof WorkspaceState.Active;
  }

  /** Moves from requested to preparing. */
  public void prepare() {
    if (!(state instanceof WorkspaceState.Requested)) {
      throw new IllegalWorkspaceStateException(id, state, "prepare");
    }
    state = new WorkspaceState.Preparing(Instant.now());
  }

  /** Moves from preparing to booting. */
  public void boot() {
    if (!(state instanceof WorkspaceState.Preparing)) {
      throw new IllegalWorkspaceStateException(id, state, "boot");
    }
    state = new WorkspaceState.Booting(Instant.now());
  }

  /** Moves from booting to ready. */
  public void markReady() {
    if (!(state instanceof WorkspaceState.Booting)) {
      throw new IllegalWorkspaceStateException(id, state, "markReady");
    }
    state = new WorkspaceState.Ready(Instant.now());
  }

  /** Starts a ready or idle workspace. */
  public void start() {
    if (!(state instanceof WorkspaceState.Ready || state instanceof WorkspaceState.Idle)) {
      throw new IllegalWorkspaceStateException(id, state, "start");
    }
    state = new WorkspaceState.Active(Instant.now());
  }

  /**
   * Marks an active workspace idle.
   *
   * @param since when the idle window started
   */
  public void markIdle(Instant since) {
    if (!(state instanceof WorkspaceState.Active)) {
      throw new IllegalWorkspaceStateException(id, state, "markIdle");
    }
    state = new WorkspaceState.Idle(Instant.now(), Objects.requireNonNull(since, "since"));
  }

  /** Suspends an active or idle workspace. The home folder is preserved. */
  public void suspend() {
    if (!(state instanceof WorkspaceState.Active || state instanceof WorkspaceState.Idle)) {
      throw new IllegalWorkspaceStateException(id, state, "suspend");
    }
    state = new WorkspaceState.Suspended(Instant.now());
  }

  /** Resumes a suspended workspace. */
  public void resume() {
    if (!(state instanceof WorkspaceState.Suspended)) {
      throw new IllegalWorkspaceStateException(id, state, "resume");
    }
    state = new WorkspaceState.Active(Instant.now());
  }

  /**
   * Parks a starting or failed workspace as suspended. The home folder is preserved, so a later
   * start resumes from the same files.
   */
  public void abortBoot() {
    if (!(state instanceof WorkspaceState.Preparing
        || state instanceof WorkspaceState.Booting
        || state instanceof WorkspaceState.Ready
        || state instanceof WorkspaceState.Failed)) {
      throw new IllegalWorkspaceStateException(id, state, "abortBoot");
    }
    state = new WorkspaceState.Suspended(Instant.now());
  }

  /**
   * Reboots a parked workspace into a fresh deployment cycle. The home folder is preserved by the
   * agent across the reboot.
   */
  public void reboot() {
    if (!(state instanceof WorkspaceState.Suspended || state instanceof WorkspaceState.Failed)) {
      throw new IllegalWorkspaceStateException(id, state, "reboot");
    }
    state = new WorkspaceState.Requested(Instant.now());
  }

  /** Destroys the workspace from any state except destroyed. */
  public void destroy() {
    if (state instanceof WorkspaceState.Destroyed) {
      throw new IllegalWorkspaceStateException(id, state, "destroy");
    }
    state = new WorkspaceState.Destroyed(Instant.now());
  }

  /**
   * Marks the workspace failed from any state except destroyed.
   *
   * @param reason the failure reason
   */
  public void fail(String reason) {
    if (state instanceof WorkspaceState.Destroyed) {
      throw new IllegalWorkspaceStateException(id, state, "fail");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    state = new WorkspaceState.Failed(Instant.now(), reason);
  }

  /**
   * Changes the logical quota.
   *
   * @param quota the new quota
   */
  public void changeQuota(WorkspaceQuota quota) {
    this.quota = Objects.requireNonNull(quota, "quota");
  }

  /**
   * Changes the resource policy.
   *
   * @param policy the new policy
   */
  public void changePolicy(ResourcePolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  /**
   * Grants a permission.
   *
   * @param user the grantee
   * @param permission the permission name
   */
  public void grant(UUID user, String permission) {
    acl = acl.withGrant(user, permission);
  }

  /**
   * Revokes a permission.
   *
   * @param user the grantee
   * @param permission the permission name
   */
  public void revoke(UUID user, String permission) {
    acl = acl.withoutGrant(user, permission);
  }

  /**
   * Records a snapshot.
   *
   * @param snapshotId the snapshot identifier
   */
  public void recordSnapshot(SnapshotId snapshotId) {
    snapshots.record(snapshotId);
  }
}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the virtual_machine table. */
@Entity
@Table(name = "virtual_machine")
public class VirtualMachineEntity {

  /** Primary key of the virtual machine. */
  @Id
  @Column(name = "vm_id")
  private UUID vmId;

  /** Owning workspace id. */
  @Column(name = "workspace_id")
  private UUID workspaceId;

  /** Hosting node id. */
  @Column(name = "node_id")
  private String nodeId;

  /** Lifecycle state. */
  @Column(name = "state")
  private String state;

  /** Allocated virtual CPU count. */
  @Column(name = "vcpu_count")
  private int vcpuCount;

  /** Allocated memory in MiB. */
  @Column(name = "memory_mib")
  private long memoryMib;

  /** Disk image path on the host. */
  @Column(name = "disk_path")
  private String diskPath;

  /** Home subvolume path on the host. */
  @Column(name = "home_path")
  private String homePath;

  /** Jail path on the host. */
  @Column(name = "jail_path")
  private String jailPath;

  /** Creation timestamp. */
  @Column(name = "created_at")
  private Instant createdAt;

  /** Destruction timestamp, null while live. */
  @Column(name = "destroyed_at")
  private Instant destroyedAt;

  /** Creates an empty instance for JPA and mapping. */
  public VirtualMachineEntity() {}

  /** Gets the primary key of the virtual machine. */
  public UUID getVmId() {
    return vmId;
  }

  /** Sets the primary key of the virtual machine. */
  public void setVmId(UUID vmId) {
    this.vmId = vmId;
  }

  /** Gets the owning workspace id. */
  public UUID getWorkspaceId() {
    return workspaceId;
  }

  /** Sets the owning workspace id. */
  public void setWorkspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
  }

  /** Gets the hosting node id. */
  public String getNodeId() {
    return nodeId;
  }

  /** Sets the hosting node id. */
  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  /** Gets the lifecycle state. */
  public String getState() {
    return state;
  }

  /** Sets the lifecycle state. */
  public void setState(String state) {
    this.state = state;
  }

  /** Gets the allocated virtual CPU count. */
  public int getVcpuCount() {
    return vcpuCount;
  }

  /** Sets the allocated virtual CPU count. */
  public void setVcpuCount(int vcpuCount) {
    this.vcpuCount = vcpuCount;
  }

  /** Gets the allocated memory in MiB. */
  public long getMemoryMib() {
    return memoryMib;
  }

  /** Sets the allocated memory in MiB. */
  public void setMemoryMib(long memoryMib) {
    this.memoryMib = memoryMib;
  }

  /** Gets the disk image path on the host. */
  public String getDiskPath() {
    return diskPath;
  }

  /** Sets the disk image path on the host. */
  public void setDiskPath(String diskPath) {
    this.diskPath = diskPath;
  }

  /** Gets the home subvolume path on the host. */
  public String getHomePath() {
    return homePath;
  }

  /** Sets the home subvolume path on the host. */
  public void setHomePath(String homePath) {
    this.homePath = homePath;
  }

  /** Gets the jail path on the host. */
  public String getJailPath() {
    return jailPath;
  }

  /** Sets the jail path on the host. */
  public void setJailPath(String jailPath) {
    this.jailPath = jailPath;
  }

  /** Gets the creation timestamp. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Sets the creation timestamp. */
  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  /** Gets the destruction timestamp. */
  public Instant getDestroyedAt() {
    return destroyedAt;
  }

  /** Sets the destruction timestamp. */
  public void setDestroyedAt(Instant destroyedAt) {
    this.destroyedAt = destroyedAt;
  }

  /** Compares by class and primary key. */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    VirtualMachineEntity that = (VirtualMachineEntity) o;
    return Objects.equals(vmId, that.vmId);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(vmId);
  }
}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/** Persistence DTO for the node table. */
@Entity
@Table(name = "node")
public class NodeEntity {

  /** Primary key of the node. */
  @Id
  @Column(name = "node_id")
  private String nodeId;

  /** Human-readable node name. */
  @Column(name = "name")
  private String name;

  /** Region hosting the node. */
  @Column(name = "region")
  private String region;

  /** Lifecycle state. */
  @Column(name = "state")
  private String state;

  /** Total CPU cores. */
  @Column(name = "cpu_cores")
  private int cpuCores;

  /** Total memory in MiB. */
  @Column(name = "memory_mib")
  private long memoryMib;

  /** Total disk in GiB. */
  @Column(name = "disk_gib")
  private long diskGib;

  /** Registration timestamp. */
  @Column(name = "registered_at")
  private Instant registeredAt;

  /** Last heartbeat timestamp, null if never seen. */
  @Column(name = "last_heartbeat")
  private Instant lastHeartbeat;

  /** Certificate fingerprint of the node. */
  @Column(name = "cert_fingerprint")
  private String certFingerprint;

  /** Creates an empty instance for JPA. */
  protected NodeEntity() {}

  /** Gets the primary key of the node. */
  public String getNodeId() {
    return nodeId;
  }

  /** Sets the primary key of the node. */
  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  /** Gets the human-readable node name. */
  public String getName() {
    return name;
  }

  /** Sets the human-readable node name. */
  public void setName(String name) {
    this.name = name;
  }

  /** Gets the region hosting the node. */
  public String getRegion() {
    return region;
  }

  /** Sets the region hosting the node. */
  public void setRegion(String region) {
    this.region = region;
  }

  /** Gets the lifecycle state. */
  public String getState() {
    return state;
  }

  /** Sets the lifecycle state. */
  public void setState(String state) {
    this.state = state;
  }

  /** Gets the total CPU cores. */
  public int getCpuCores() {
    return cpuCores;
  }

  /** Sets the total CPU cores. */
  public void setCpuCores(int cpuCores) {
    this.cpuCores = cpuCores;
  }

  /** Gets the total memory in MiB. */
  public long getMemoryMib() {
    return memoryMib;
  }

  /** Sets the total memory in MiB. */
  public void setMemoryMib(long memoryMib) {
    this.memoryMib = memoryMib;
  }

  /** Gets the total disk in GiB. */
  public long getDiskGib() {
    return diskGib;
  }

  /** Sets the total disk in GiB. */
  public void setDiskGib(long diskGib) {
    this.diskGib = diskGib;
  }

  /** Gets the registration timestamp. */
  public Instant getRegisteredAt() {
    return registeredAt;
  }

  /** Sets the registration timestamp. */
  public void setRegisteredAt(Instant registeredAt) {
    this.registeredAt = registeredAt;
  }

  /** Gets the last heartbeat timestamp. */
  public Instant getLastHeartbeat() {
    return lastHeartbeat;
  }

  /** Sets the last heartbeat timestamp. */
  public void setLastHeartbeat(Instant lastHeartbeat) {
    this.lastHeartbeat = lastHeartbeat;
  }

  /** Gets the certificate fingerprint of the node. */
  public String getCertFingerprint() {
    return certFingerprint;
  }

  /** Sets the certificate fingerprint of the node. */
  public void setCertFingerprint(String certFingerprint) {
    this.certFingerprint = certFingerprint;
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
    NodeEntity that = (NodeEntity) o;
    return Objects.equals(nodeId, that.nodeId);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(nodeId);
  }
}

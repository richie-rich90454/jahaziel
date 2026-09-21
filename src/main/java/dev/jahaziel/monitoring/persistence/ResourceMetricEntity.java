/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the resource_metric_hourly table. */
@Entity
@Table(name = "resource_metric_hourly")
public class ResourceMetricEntity {

  /** Composite key of the hourly rollup. */
  @EmbeddedId private Id id;

  /** Average CPU percent over the hour. */
  @Column(name = "cpu_avg_percent")
  private float cpuAvgPercent;

  /** Maximum CPU percent over the hour. */
  @Column(name = "cpu_max_percent")
  private float cpuMaxPercent;

  /** Average memory in MiB over the hour. */
  @Column(name = "memory_avg_mib")
  private long memoryAvgMib;

  /** Maximum memory in MiB over the hour. */
  @Column(name = "memory_max_mib")
  private long memoryMaxMib;

  /** Average disk read bytes per second. */
  @Column(name = "disk_read_avg_bps")
  private long diskReadAvgBps;

  /** Average disk write bytes per second. */
  @Column(name = "disk_write_avg_bps")
  private long diskWriteAvgBps;

  /** Average network receive bytes per second. */
  @Column(name = "network_rx_avg_bps")
  private long networkRxAvgBps;

  /** Average network transmit bytes per second. */
  @Column(name = "network_tx_avg_bps")
  private long networkTxAvgBps;

  /** Maximum process count over the hour. */
  @Column(name = "process_max")
  private int processMax;

  /** Creates an empty instance for JPA. */
  public ResourceMetricEntity() {}

  /** Gets the composite key of the hourly rollup. */
  public Id getId() {
    return id;
  }

  /** Sets the composite key of the hourly rollup. */
  public void setId(Id id) {
    this.id = id;
  }

  /** Gets the average CPU percent over the hour. */
  public float getCpuAvgPercent() {
    return cpuAvgPercent;
  }

  /** Sets the average CPU percent over the hour. */
  public void setCpuAvgPercent(float cpuAvgPercent) {
    this.cpuAvgPercent = cpuAvgPercent;
  }

  /** Gets the maximum CPU percent over the hour. */
  public float getCpuMaxPercent() {
    return cpuMaxPercent;
  }

  /** Sets the maximum CPU percent over the hour. */
  public void setCpuMaxPercent(float cpuMaxPercent) {
    this.cpuMaxPercent = cpuMaxPercent;
  }

  /** Gets the average memory in MiB over the hour. */
  public long getMemoryAvgMib() {
    return memoryAvgMib;
  }

  /** Sets the average memory in MiB over the hour. */
  public void setMemoryAvgMib(long memoryAvgMib) {
    this.memoryAvgMib = memoryAvgMib;
  }

  /** Gets the maximum memory in MiB over the hour. */
  public long getMemoryMaxMib() {
    return memoryMaxMib;
  }

  /** Sets the maximum memory in MiB over the hour. */
  public void setMemoryMaxMib(long memoryMaxMib) {
    this.memoryMaxMib = memoryMaxMib;
  }

  /** Gets the average disk read bytes per second. */
  public long getDiskReadAvgBps() {
    return diskReadAvgBps;
  }

  /** Sets the average disk read bytes per second. */
  public void setDiskReadAvgBps(long diskReadAvgBps) {
    this.diskReadAvgBps = diskReadAvgBps;
  }

  /** Gets the average disk write bytes per second. */
  public long getDiskWriteAvgBps() {
    return diskWriteAvgBps;
  }

  /** Sets the average disk write bytes per second. */
  public void setDiskWriteAvgBps(long diskWriteAvgBps) {
    this.diskWriteAvgBps = diskWriteAvgBps;
  }

  /** Gets the average network receive bytes per second. */
  public long getNetworkRxAvgBps() {
    return networkRxAvgBps;
  }

  /** Sets the average network receive bytes per second. */
  public void setNetworkRxAvgBps(long networkRxAvgBps) {
    this.networkRxAvgBps = networkRxAvgBps;
  }

  /** Gets the average network transmit bytes per second. */
  public long getNetworkTxAvgBps() {
    return networkTxAvgBps;
  }

  /** Sets the average network transmit bytes per second. */
  public void setNetworkTxAvgBps(long networkTxAvgBps) {
    this.networkTxAvgBps = networkTxAvgBps;
  }

  /** Gets the maximum process count over the hour. */
  public int getProcessMax() {
    return processMax;
  }

  /** Sets the maximum process count over the hour. */
  public void setProcessMax(int processMax) {
    this.processMax = processMax;
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
    ResourceMetricEntity that = (ResourceMetricEntity) o;
    return Objects.equals(id, that.id);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /** Composite key of the resource_metric_hourly table. */
  @Embeddable
  public static class Id implements Serializable {

    /** Serial version UID. */
    private static final long serialVersionUID = 1L;

    /** Workspace id. */
    @Column(name = "workspace_id")
    private UUID workspaceId;

    /** Start of the rolled-up hour. */
    @Column(name = "hour_start")
    private Instant hourStart;

    /** Creates an empty key for JPA. */
    public Id() {}

    /** Creates a key with all fields. */
    public Id(UUID workspaceId, Instant hourStart) {
      this.workspaceId = workspaceId;
      this.hourStart = hourStart;
    }

    /** Gets the workspace id. */
    public UUID getWorkspaceId() {
      return workspaceId;
    }

    /** Sets the workspace id. */
    public void setWorkspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
    }

    /** Gets the start of the rolled-up hour. */
    public Instant getHourStart() {
      return hourStart;
    }

    /** Sets the start of the rolled-up hour. */
    public void setHourStart(Instant hourStart) {
      this.hourStart = hourStart;
    }

    /** Compares by all key fields. */
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Id that = (Id) o;
      return Objects.equals(workspaceId, that.workspaceId)
          && Objects.equals(hourStart, that.hourStart);
    }

    /** Returns the hash code derived from all key fields. */
    @Override
    public int hashCode() {
      return Objects.hash(workspaceId, hourStart);
    }
  }
}

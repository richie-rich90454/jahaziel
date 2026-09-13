/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the audit_process table. */
@Entity
@Table(name = "audit_process")
public class AuditProcessEntity {

  /** Composite key of the process record. */
  @EmbeddedId private Id id;

  /** Workspace id. */
  @Column(name = "workspace_id")
  private UUID workspaceId;

  /** Process id. */
  @Column(name = "pid")
  private int pid;

  /** Parent process id, null when unknown. */
  @Column(name = "ppid")
  private Integer ppid;

  /** Executable path. */
  @Column(name = "exe_path")
  private String exePath;

  /** Argument vector, stored as JSON text so Postgres and SQLite share one mapping. */
  @Column(name = "argv")
  @Convert(converter = StringArrayConverter.class)
  private String[] argv;

  /** Exit code, null while running. */
  @Column(name = "exit_code")
  private Integer exitCode;

  /** Process start time. */
  @Column(name = "start_time")
  private Instant startTime;

  /** Process end time, null while running. */
  @Column(name = "end_time")
  private Instant endTime;

  /** User id. */
  @Column(name = "uid")
  private int uid;

  /** Group id. */
  @Column(name = "gid")
  private int gid;

  /** Capabilities, null when not captured, stored as JSON text. */
  @Column(name = "capabilities")
  @Convert(converter = StringArrayConverter.class)
  private String[] capabilities;

  /** Creates an empty instance for JPA. */
  public AuditProcessEntity() {}

  /** Gets the composite key of the process record. */
  public Id getId() {
    return id;
  }

  /** Sets the composite key of the process record. */
  public void setId(Id id) {
    this.id = id;
  }

  /** Gets the workspace id. */
  public UUID getWorkspaceId() {
    return workspaceId;
  }

  /** Sets the workspace id. */
  public void setWorkspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
  }

  /** Gets the process id. */
  public int getPid() {
    return pid;
  }

  /** Sets the process id. */
  public void setPid(int pid) {
    this.pid = pid;
  }

  /** Gets the parent process id. */
  public Integer getPpid() {
    return ppid;
  }

  /** Sets the parent process id. */
  public void setPpid(Integer ppid) {
    this.ppid = ppid;
  }

  /** Gets the executable path. */
  public String getExePath() {
    return exePath;
  }

  /** Sets the executable path. */
  public void setExePath(String exePath) {
    this.exePath = exePath;
  }

  /** Gets the argument vector. */
  public String[] getArgv() {
    return argv;
  }

  /** Sets the argument vector. */
  public void setArgv(String[] argv) {
    this.argv = argv;
  }

  /** Gets the exit code. */
  public Integer getExitCode() {
    return exitCode;
  }

  /** Sets the exit code. */
  public void setExitCode(Integer exitCode) {
    this.exitCode = exitCode;
  }

  /** Gets the process start time. */
  public Instant getStartTime() {
    return startTime;
  }

  /** Sets the process start time. */
  public void setStartTime(Instant startTime) {
    this.startTime = startTime;
  }

  /** Gets the process end time. */
  public Instant getEndTime() {
    return endTime;
  }

  /** Sets the process end time. */
  public void setEndTime(Instant endTime) {
    this.endTime = endTime;
  }

  /** Gets the user id. */
  public int getUid() {
    return uid;
  }

  /** Sets the user id. */
  public void setUid(int uid) {
    this.uid = uid;
  }

  /** Gets the group id. */
  public int getGid() {
    return gid;
  }

  /** Sets the group id. */
  public void setGid(int gid) {
    this.gid = gid;
  }

  /** Gets the capabilities. */
  public String[] getCapabilities() {
    return capabilities;
  }

  /** Sets the capabilities. */
  public void setCapabilities(String[] capabilities) {
    this.capabilities = capabilities;
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
    AuditProcessEntity that = (AuditProcessEntity) o;
    return Objects.equals(id, that.id);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /** Composite key of the audit_process table. */
  @Embeddable
  public static class Id implements Serializable {

    /** Serial version UID. */
    private static final long serialVersionUID = 1L;

    /** Record id. */
    @Column(name = "record_id")
    private UUID recordId;

    /** Record timestamp. */
    @Column(name = "timestamp")
    private Instant timestamp;

    /** Creates an empty key for JPA. */
    public Id() {}

    /** Creates a key with all fields. */
    public Id(UUID recordId, Instant timestamp) {
      this.recordId = recordId;
      this.timestamp = timestamp;
    }

    /** Gets the record id. */
    public UUID getRecordId() {
      return recordId;
    }

    /** Sets the record id. */
    public void setRecordId(UUID recordId) {
      this.recordId = recordId;
    }

    /** Gets the record timestamp. */
    public Instant getTimestamp() {
      return timestamp;
    }

    /** Sets the record timestamp. */
    public void setTimestamp(Instant timestamp) {
      this.timestamp = timestamp;
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
      return Objects.equals(recordId, that.recordId) && Objects.equals(timestamp, that.timestamp);
    }

    /** Returns the hash code derived from all key fields. */
    @Override
    public int hashCode() {
      return Objects.hash(recordId, timestamp);
    }
  }
}

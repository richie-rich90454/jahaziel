/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the audit_terminal table. */
@Entity
@Table(name = "audit_terminal")
public class AuditTerminalEntity {

  /** Composite key of the terminal record. */
  @EmbeddedId private Id id;

  /** Linked audit event id. */
  @Column(name = "event_id")
  private UUID eventId;

  /** Acting user id. */
  @Column(name = "user_id")
  private UUID userId;

  /** Workspace id. */
  @Column(name = "workspace_id")
  private UUID workspaceId;

  /** Terminal session id. */
  @Column(name = "terminal_session_id")
  private UUID terminalSessionId;

  /** Executed command. */
  @Column(name = "command")
  private String command;

  /** Process exit code. */
  @Column(name = "exit_code")
  private int exitCode;

  /** Working directory. */
  @Column(name = "cwd")
  private String cwd;

  /** Environment hash, null when not captured. */
  @Column(name = "env_hash")
  private String envHash;

  /** Duration in milliseconds, null when unknown. */
  @Column(name = "duration_ms")
  private Long durationMs;

  /** Stdout object id, null when not captured. */
  @Column(name = "stdout_object_id")
  private String stdoutObjectId;

  /** Stderr object id, null when not captured. */
  @Column(name = "stderr_object_id")
  private String stderrObjectId;

  /** Stdout size in bytes, null when unknown. */
  @Column(name = "stdout_size")
  private Long stdoutSize;

  /** Stderr size in bytes, null when unknown. */
  @Column(name = "stderr_size")
  private Long stderrSize;

  /** Creates an empty instance for JPA. */
  protected AuditTerminalEntity() {}

  /** Gets the composite key of the terminal record. */
  public Id getId() {
    return id;
  }

  /** Sets the composite key of the terminal record. */
  public void setId(Id id) {
    this.id = id;
  }

  /** Gets the linked audit event id. */
  public UUID getEventId() {
    return eventId;
  }

  /** Sets the linked audit event id. */
  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  /** Gets the acting user id. */
  public UUID getUserId() {
    return userId;
  }

  /** Sets the acting user id. */
  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  /** Gets the workspace id. */
  public UUID getWorkspaceId() {
    return workspaceId;
  }

  /** Sets the workspace id. */
  public void setWorkspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
  }

  /** Gets the terminal session id. */
  public UUID getTerminalSessionId() {
    return terminalSessionId;
  }

  /** Sets the terminal session id. */
  public void setTerminalSessionId(UUID terminalSessionId) {
    this.terminalSessionId = terminalSessionId;
  }

  /** Gets the executed command. */
  public String getCommand() {
    return command;
  }

  /** Sets the executed command. */
  public void setCommand(String command) {
    this.command = command;
  }

  /** Gets the process exit code. */
  public int getExitCode() {
    return exitCode;
  }

  /** Sets the process exit code. */
  public void setExitCode(int exitCode) {
    this.exitCode = exitCode;
  }

  /** Gets the working directory. */
  public String getCwd() {
    return cwd;
  }

  /** Sets the working directory. */
  public void setCwd(String cwd) {
    this.cwd = cwd;
  }

  /** Gets the environment hash. */
  public String getEnvHash() {
    return envHash;
  }

  /** Sets the environment hash. */
  public void setEnvHash(String envHash) {
    this.envHash = envHash;
  }

  /** Gets the duration in milliseconds. */
  public Long getDurationMs() {
    return durationMs;
  }

  /** Sets the duration in milliseconds. */
  public void setDurationMs(Long durationMs) {
    this.durationMs = durationMs;
  }

  /** Gets the stdout object id. */
  public String getStdoutObjectId() {
    return stdoutObjectId;
  }

  /** Sets the stdout object id. */
  public void setStdoutObjectId(String stdoutObjectId) {
    this.stdoutObjectId = stdoutObjectId;
  }

  /** Gets the stderr object id. */
  public String getStderrObjectId() {
    return stderrObjectId;
  }

  /** Sets the stderr object id. */
  public void setStderrObjectId(String stderrObjectId) {
    this.stderrObjectId = stderrObjectId;
  }

  /** Gets the stdout size in bytes. */
  public Long getStdoutSize() {
    return stdoutSize;
  }

  /** Sets the stdout size in bytes. */
  public void setStdoutSize(Long stdoutSize) {
    this.stdoutSize = stdoutSize;
  }

  /** Gets the stderr size in bytes. */
  public Long getStderrSize() {
    return stderrSize;
  }

  /** Sets the stderr size in bytes. */
  public void setStderrSize(Long stderrSize) {
    this.stderrSize = stderrSize;
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
    AuditTerminalEntity that = (AuditTerminalEntity) o;
    return Objects.equals(id, that.id);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /** Composite key of the audit_terminal table. */
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
    protected Id() {}

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

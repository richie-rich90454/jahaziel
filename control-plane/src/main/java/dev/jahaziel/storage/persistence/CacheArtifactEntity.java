/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.storage.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the cache_artifact table. */
@Entity
@Table(name = "cache_artifact")
public class CacheArtifactEntity {

  /** Primary key of the cache artifact. */
  @Id
  @Column(name = "artifact_id")
  private UUID artifactId;

  /** Tool hosting the artifact. */
  @Column(name = "tool")
  private String tool;

  /** Tool-specific coordinate. */
  @Column(name = "coordinate")
  private String coordinate;

  /** SHA-256 of the artifact content. */
  @Column(name = "sha256")
  private String sha256;

  /** Size in bytes. */
  @Column(name = "size_bytes")
  private long sizeBytes;

  /** Path on the host. */
  @Column(name = "path_on_host")
  private String pathOnHost;

  /** Population timestamp. */
  @Column(name = "populated_at")
  private Instant populatedAt;

  /** Population source. */
  @Column(name = "source")
  private String source;

  /** Creates an empty instance for JPA. */
  public CacheArtifactEntity() {}

  /** Gets the primary key of the cache artifact. */
  public UUID getArtifactId() {
    return artifactId;
  }

  /** Sets the primary key of the cache artifact. */
  public void setArtifactId(UUID artifactId) {
    this.artifactId = artifactId;
  }

  /** Gets the tool hosting the artifact. */
  public String getTool() {
    return tool;
  }

  /** Sets the tool hosting the artifact. */
  public void setTool(String tool) {
    this.tool = tool;
  }

  /** Gets the tool-specific coordinate. */
  public String getCoordinate() {
    return coordinate;
  }

  /** Sets the tool-specific coordinate. */
  public void setCoordinate(String coordinate) {
    this.coordinate = coordinate;
  }

  /** Gets the SHA-256 of the artifact content. */
  public String getSha256() {
    return sha256;
  }

  /** Sets the SHA-256 of the artifact content. */
  public void setSha256(String sha256) {
    this.sha256 = sha256;
  }

  /** Gets the size in bytes. */
  public long getSizeBytes() {
    return sizeBytes;
  }

  /** Sets the size in bytes. */
  public void setSizeBytes(long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  /** Gets the path on the host. */
  public String getPathOnHost() {
    return pathOnHost;
  }

  /** Sets the path on the host. */
  public void setPathOnHost(String pathOnHost) {
    this.pathOnHost = pathOnHost;
  }

  /** Gets the population timestamp. */
  public Instant getPopulatedAt() {
    return populatedAt;
  }

  /** Sets the population timestamp. */
  public void setPopulatedAt(Instant populatedAt) {
    this.populatedAt = populatedAt;
  }

  /** Gets the population source. */
  public String getSource() {
    return source;
  }

  /** Sets the population source. */
  public void setSource(String source) {
    this.source = source;
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
    CacheArtifactEntity that = (CacheArtifactEntity) o;
    return Objects.equals(artifactId, that.artifactId);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(artifactId);
  }
}

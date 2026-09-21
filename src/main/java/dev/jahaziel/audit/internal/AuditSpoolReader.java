/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Drains node spool files into the audit log. Spool lines carry guest actions; the reader stamps
 * identifiers, timestamps, and the workspace link the guest cannot forge.
 */
public final class AuditSpoolReader {
  private final AuditService audit;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates the reader.
   *
   * @param audit the audit log
   */
  public AuditSpoolReader(AuditService audit) {
    this.audit = Objects.requireNonNull(audit, "audit");
  }

  /**
   * Drains one spool file, deleting it afterwards. Every line is validated before anything is
   * appended, so a malformed line fails the whole file without ingesting duplicates on retry.
   *
   * @param workspaceId the workspace the file belongs to
   * @param spoolFile the spooled JSON lines file
   * @return the ingested event count
   */
  public int drain(WorkspaceId workspaceId, Path spoolFile) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(spoolFile, "spoolFile");
    List<String> lines;
    try {
      lines = Files.readAllLines(spoolFile);
    } catch (Exception failure) {
      throw new IllegalArgumentException("unreadable spool file: " + spoolFile, failure);
    }
    List<AuditRecord> records =
        lines.stream()
            .filter(line -> !line.isBlank())
            .map(line -> toRecord(workspaceId, line))
            .toList();
    for (AuditRecord record : records) {
      audit.append(record);
    }
    try {
      Files.deleteIfExists(spoolFile);
    } catch (Exception failure) {
      throw new IllegalStateException("cannot remove drained spool file: " + spoolFile, failure);
    }
    return records.size();
  }

  /**
   * Lists spooled files in a directory.
   *
   * @param stagingDir the staging directory
   * @return the JSON lines files
   */
  public static List<Path> spooled(Path stagingDir) {
    try (Stream<Path> files = Files.list(stagingDir)) {
      return files.filter(path -> path.toString().endsWith(".jsonl")).toList();
    } catch (Exception failure) {
      throw new IllegalArgumentException("unreadable staging dir: " + stagingDir, failure);
    }
  }

  private AuditRecord toRecord(WorkspaceId workspaceId, String line) {
    JsonNode json;
    try {
      json = mapper.readTree(line);
    } catch (Exception failure) {
      throw new IllegalArgumentException("malformed spool line: " + line, failure);
    }
    String action = text(json, "action");
    return new AuditRecord(
        UUID.randomUUID(),
        Instant.now(),
        "guest:" + workspaceId.value(),
        ActorType.NODE,
        action,
        Optional.of(workspaceId.value().toString()),
        Optional.of("workspace"),
        Outcome.SUCCESS,
        Optional.empty(),
        Optional.empty(),
        Map.of(),
        Optional.empty(),
        "");
  }

  private static String text(JsonNode json, String field) {
    JsonNode node = json.get(field);
    if (node == null || !node.isString()) {
      throw new IllegalArgumentException("spool line misses string field: " + field);
    }
    return node.asString();
  }
}

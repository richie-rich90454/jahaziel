/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Drains node spool files into the audit log. Spool lines carry guest actions; the reader stamps
 * identifiers, timestamps, and the workspace link the guest cannot forge. Event ids are derived
 * from the workspace, line number and line content so a retried drain is idempotent.
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
     * appended, so a malformed line fails the whole file without ingesting duplicates on retry. Raw
     * spool lines are never logged; only the offending line number is reported.
     *
     * @param workspaceId the workspace the file belongs to
     * @param spoolFile the spooled JSON lines file
     * @return the ingested event count
     */
    public int drain(UUID workspaceId, Path spoolFile) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(spoolFile, "spoolFile");
        List<String> lines;
        try {
            lines = Files.readAllLines(spoolFile);
        } catch (Exception failure) {
            throw new IllegalArgumentException("unreadable spool file: " + spoolFile, failure);
        }
        List<AuditRecord> records = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                records.add(toRecord(workspaceId, lines.get(i), i + 1));
            }
        }
        for (AuditRecord record : records) {
            audit.append(record);
        }
        try {
            Files.deleteIfExists(spoolFile);
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "cannot remove drained spool file: " + spoolFile, failure);
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
            List<Path> result = new ArrayList<>();
            Iterator<Path> iterator = files.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (path.toString().endsWith(".jsonl")) {
                    result.add(path);
                }
            }
            return result;
        } catch (Exception failure) {
            throw new IllegalArgumentException("unreadable staging dir: " + stagingDir, failure);
        }
    }

    private AuditRecord toRecord(UUID workspaceId, String line, int lineNumber) {
        JsonNode json;
        try {
            json = mapper.readTree(line);
        } catch (Exception failure) {
            throw new IllegalArgumentException(
                    "malformed spool line " + lineNumber + " in " + workspaceId, failure);
        }
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException("spool line " + lineNumber + " is not an object");
        }
        String action = text(json, "action", lineNumber);
        UUID eventId = deterministicId(workspaceId, lineNumber, line);
        Instant timestamp = instant(json, lineNumber);
        String actor = optionalText(json, "actor", "guest:" + workspaceId);
        ActorType actorType = actorType(json);
        String target = optionalText(json, "target", workspaceId.toString());
        String targetType = optionalText(json, "targetType", "workspace");
        Outcome outcome = outcome(json);
        Optional<String> sourceIp = optional(json, "sourceIp");
        Optional<String> sourceNode = optional(json, "sourceNode");
        Map<String, String> details = details(json, lineNumber);
        return new AuditRecord(
                eventId,
                timestamp,
                actor,
                actorType,
                action,
                Optional.of(target),
                Optional.of(targetType),
                outcome,
                sourceIp,
                sourceNode,
                details,
                Optional.empty(),
                "");
    }

    private static UUID deterministicId(UUID workspaceId, int lineNumber, String line) {
        String key = workspaceId + "\u001f" + lineNumber + "\u001f" + line.strip();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static Instant instant(JsonNode json, int lineNumber) {
        String value = optionalText(json, "timestamp", "");
        if (value.isEmpty()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException(
                    "bad timestamp on spool line " + lineNumber, invalid);
        }
    }

    private static String text(JsonNode json, String field, int lineNumber) {
        JsonNode node = json.get(field);
        if (node == null || !node.isString()) {
            throw new IllegalArgumentException(
                    "spool line " + lineNumber + " misses string field: " + field);
        }
        return node.asString();
    }

    private static String optionalText(JsonNode json, String field, String fallback) {
        JsonNode node = json.get(field);
        return node != null && node.isString() && !node.asString().isEmpty()
                ? node.asString()
                : fallback;
    }

    private static Optional<String> optional(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node != null && node.isString() && !node.asString().isEmpty()
                ? Optional.of(node.asString())
                : Optional.empty();
    }

    private static ActorType actorType(JsonNode json) {
        JsonNode node = json.get("actorType");
        if (node != null && node.isString()) {
            try {
                return ActorType.valueOf(node.asString());
            } catch (IllegalArgumentException unknown) {
                return ActorType.NODE;
            }
        }
        return ActorType.NODE;
    }

    private static Outcome outcome(JsonNode json) {
        JsonNode node = json.get("outcome");
        if (node != null && node.isString()) {
            try {
                return Outcome.valueOf(node.asString());
            } catch (IllegalArgumentException unknown) {
                return Outcome.SUCCESS;
            }
        }
        return Outcome.SUCCESS;
    }

    private static Map<String, String> details(JsonNode json, int lineNumber) {
        JsonNode node = json.get("details");
        if (node == null) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException(
                    "spool line " + lineNumber + " has non-object details");
        }
        Map<String, String> values = new LinkedHashMap<String, String>();
        Iterator<String> names = node.propertyNames().iterator();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode value = node.get(name);
            values.put(name, value == null ? "" : value.asString());
        }
        return Map.copyOf(values);
    }
}

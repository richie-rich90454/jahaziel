/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JSON-lines log writer. Files rotate by size or calendar day under {@code data/logs}. Secret-like
 * keys are dropped and token values are masked before anything reaches disk.
 */
public final class StructuredLogger {
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final Path logDir;
    private final long maxBytes;
    private final Clock clock;
    private String currentDay;

    /** Creates a logger with a 1 MiB rotation threshold and the system clock. */
    public StructuredLogger(Path dataDir) {
        this(dataDir, 1024L * 1024L, Clock.systemUTC());
    }

    /** Creates a logger with an explicit size threshold and clock. */
    public StructuredLogger(Path dataDir, long maxBytes, Clock clock) {
        if (dataDir == null || maxBytes <= 0) {
            throw new IllegalArgumentException("dataDir must be set and maxBytes positive");
        }
        this.logDir = dataDir.toAbsolutePath().normalize().resolve("logs");
        this.maxBytes = maxBytes;
        this.clock = clock;
        OpsFiles.ensureDir(this.logDir);
        this.currentDay = day();
    }

    /** Returns the directory holding log files. */
    public Path logDir() {
        return logDir;
    }

    /** Returns the active log file. */
    public Path currentFile() {
        return logDir.resolve("jahaziel.jsonl");
    }

    /** Appends one structured event, rotating first when needed. */
    public void log(String level, String event, Map<String, String> fields) {
        Path active = currentFile();
        String line = render(level, event, fields) + "\n";
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (Files.exists(active)
                && (sizeOf(active) + bytes.length > maxBytes || !day().equals(currentDay))) {
            rotate(active);
        }
        try {
            Files.write(active, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot write log entry", failure);
        }
        currentDay = day();
    }

    /** Returns every log file, active and rotated, sorted by name. */
    public List<Path> files() {
        return OpsFiles.listFiles(logDir);
    }

    private String render(String level, String event, Map<String, String> fields) {
        StringBuilder out = new StringBuilder();
        out.append("{\"ts\":\"")
                .append(OpsFiles.jsonEscape(Instant.now(clock).toString()))
                .append('"');
        out.append(",\"level\":\"")
                .append(OpsFiles.jsonEscape(level == null ? "INFO" : level))
                .append('"');
        out.append(",\"event\":\"")
                .append(OpsFiles.jsonEscape(event == null ? "" : event))
                .append('"');
        out.append(",\"fields\":{");
        List<String> keys = fields == null ? new ArrayList<String>() : OpsFiles.sortedKeys(fields);
        boolean first = true;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (OpsFiles.isSecretKey(key)) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(OpsFiles.jsonEscape(key)).append("\":");
            out.append('"')
                    .append(OpsFiles.jsonEscape(OpsFiles.sanitizeText(fields.get(key))))
                    .append('"');
        }
        out.append("}}");
        return out.toString();
    }

    private void rotate(Path active) {
        String stamp = Instant.now(clock).atZone(ZoneId.of("UTC")).format(STAMP);
        Path target = logDir.resolve("jahaziel-" + stamp + ".jsonl");
        int suffix = 1;
        while (Files.exists(target)) {
            target = logDir.resolve("jahaziel-" + stamp + "-" + suffix + ".jsonl");
            suffix++;
        }
        try {
            Files.move(active, target);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot rotate log file", failure);
        }
    }

    private long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException failure) {
            return 0;
        }
    }

    private String day() {
        return Instant.now(clock).atZone(ZoneId.of("UTC")).format(DAY);
    }
}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Result model for dependency scanning, with references to the SBOMs the findings came from. The
 * store writes only under the jar data directory's {@code audit/} folder; scanning itself lives
 * elsewhere.
 */
public final class DependencyAuditReport {

    public static final String AUDIT_DIRECTORY = "audit";
    public static final String REPORT_FILE = "dependency-audit.txt";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private String generatedAt = "";
    private boolean passed;
    private int criticalCount;
    private int highCount;
    private final ArrayList<String> findings = new ArrayList<String>();
    private final ArrayList<String> sbomReferences = new ArrayList<String>();

    /** Creates an empty report. */
    public DependencyAuditReport() {}

    /** Creates a report with its headline counters. */
    public DependencyAuditReport(
            String generatedAt, boolean passed, int criticalCount, int highCount) {
        this.generatedAt = generatedAt == null ? "" : generatedAt;
        this.passed = passed;
        this.criticalCount = criticalCount;
        this.highCount = highCount;
    }

    /** Returns the scan timestamp. */
    public String generatedAt() {
        return generatedAt;
    }

    /** Returns whether the scan passed. */
    public boolean passed() {
        return passed;
    }

    /** Returns the critical finding count. */
    public int criticalCount() {
        return criticalCount;
    }

    /** Returns the high finding count. */
    public int highCount() {
        return highCount;
    }

    /** Returns a copy of the finding list. */
    public ArrayList<String> findings() {
        return new ArrayList<String>(findings);
    }

    /** Returns a copy of the SBOM reference list. */
    public ArrayList<String> sbomReferences() {
        return new ArrayList<String>(sbomReferences);
    }

    /** Adds a finding. */
    public void addFinding(String finding) {
        if (finding != null && !finding.isEmpty()) findings.add(finding);
    }

    /** Adds a reference to an SBOM file under the data directory. */
    public void addSbomReference(String reference) {
        if (reference != null && !reference.isEmpty()) sbomReferences.add(reference);
    }

    /** Writes the report under {@code dataDir/audit/}. */
    public void save(Path dataDir) {
        if (dataDir == null) throw new IllegalArgumentException("dataDir");
        StringBuilder out = new StringBuilder();
        out.append("generatedAt=").append(escape(generatedAt)).append('\n');
        out.append("passed=").append(passed).append('\n');
        out.append("critical=").append(criticalCount).append('\n');
        out.append("high=").append(highCount).append('\n');
        for (int i = 0; i < findings.size(); i++) {
            out.append("finding=").append(escape(findings.get(i))).append('\n');
        }
        for (int i = 0; i < sbomReferences.size(); i++) {
            out.append("sbom=").append(escape(sbomReferences.get(i))).append('\n');
        }
        Path file = reportPath(dataDir);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, out.toString().getBytes(UTF8));
        } catch (IOException failed) {
            throw new IllegalStateException("cannot write " + file, failed);
        }
    }

    /** Reads the report from {@code dataDir/audit/}, or returns an empty report when absent. */
    public static DependencyAuditReport load(Path dataDir) {
        if (dataDir == null) throw new IllegalArgumentException("dataDir");
        Path file = reportPath(dataDir);
        if (!Files.exists(file)) return new DependencyAuditReport();
        DependencyAuditReport report = new DependencyAuditReport();
        try {
            String content = new String(Files.readAllBytes(file), UTF8);
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.startsWith("generatedAt=")) {
                    report.generatedAt = unescape(line.substring("generatedAt=".length()));
                } else if (line.startsWith("passed=")) {
                    report.passed = Boolean.parseBoolean(line.substring("passed=".length()));
                } else if (line.startsWith("critical=")) {
                    report.criticalCount = integer(line.substring("critical=".length()));
                } else if (line.startsWith("high=")) {
                    report.highCount = integer(line.substring("high=".length()));
                } else if (line.startsWith("finding=")) {
                    report.addFinding(unescape(line.substring("finding=".length())));
                } else if (line.startsWith("sbom=")) {
                    report.addSbomReference(unescape(line.substring("sbom=".length())));
                }
            }
        } catch (IOException failed) {
            throw new IllegalStateException("cannot read " + file, failed);
        }
        return report;
    }

    private static Path reportPath(Path dataDir) {
        return Paths.get(dataDir.toString(), AUDIT_DIRECTORY, REPORT_FILE);
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else out.append(c);
        }
        return out.toString();
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                i++;
                char next = value.charAt(i);
                if (next == 'n') out.append('\n');
                else if (next == 'r') out.append('\r');
                else out.append(next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyAuditReportTest {
    @TempDir Path temporary;

    @Test
    void roundTripsUnderDataAuditDir() {
        DependencyAuditReport report =
                new DependencyAuditReport("2026-11-19T00:00:00Z", false, 1, 2);
        report.addFinding("CVE-2026-0001 in libA");
        report.addSbomReference("data/audit/sbom.cdx.json");

        report.save(temporary);

        Path file = Paths.get(temporary.toString(), "audit", "dependency-audit.txt");
        assertTrue(Files.exists(file));

        DependencyAuditReport loaded = DependencyAuditReport.load(temporary);
        assertEquals("2026-11-19T00:00:00Z", loaded.generatedAt());
        assertFalse(loaded.passed());
        assertEquals(1, loaded.criticalCount());
        assertEquals(2, loaded.highCount());
        assertEquals(1, loaded.findings().size());
        assertEquals("CVE-2026-0001 in libA", loaded.findings().get(0));
        assertEquals(1, loaded.sbomReferences().size());
        assertEquals("data/audit/sbom.cdx.json", loaded.sbomReferences().get(0));
    }

    @Test
    void missingReportLoadsEmpty() {
        DependencyAuditReport loaded = DependencyAuditReport.load(temporary);

        assertEquals(0, loaded.findings().size());
        assertTrue(loaded.passed() == false);
    }
}

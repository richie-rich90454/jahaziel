/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Behavior of {@link StructuredLogger}: JSON shape, rotation and secret suppression. */
class StructuredLoggerTest {
    @TempDir Path temp;

    @Test
    void rotatesAndNeverWritesSecrets() throws Exception {
        TestClock clock = new TestClock("2026-01-01T00:00:00Z");
        StructuredLogger logger = new StructuredLogger(temp.resolve("data"), 220L, clock);
        Map<String, String> fields = new HashMap<String, String>();
        fields.put("user", "alice");
        fields.put("password", "hunter2");
        fields.put("note", "token=abc.def.ghi and Bearer zzz.yyy.xxx");
        for (int i = 0; i < 6; i++) {
            logger.log("INFO", "event-" + i, fields);
        }
        List<Path> files = logger.files();
        assertTrue(files.size() > 1, "expected rotation, got " + files.size());
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < files.size(); i++) {
            all.append(OpsFiles.readText(files.get(i)));
        }
        assertFalse(all.toString().contains("hunter2"));
        assertFalse(all.toString().contains("abc.def.ghi"));
        assertFalse(all.toString().contains("zzz.yyy.xxx"));
        assertFalse(all.toString().contains("password"));
        assertTrue(all.toString().contains("\"event\":\"event-5\""));
        assertTrue(all.toString().contains("\"user\":\"alice\""));
    }

    @Test
    void rotatesOnDayChange() throws Exception {
        TestClock clock = new TestClock("2026-01-01T23:59:59Z");
        StructuredLogger logger = new StructuredLogger(temp.resolve("data"), 100000L, clock);
        logger.log("INFO", "before", new HashMap<String, String>());
        clock.advanceSeconds(2);
        logger.log("INFO", "after", new HashMap<String, String>());
        assertEquals(2, logger.files().size());
    }
}

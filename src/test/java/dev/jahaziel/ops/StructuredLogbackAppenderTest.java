/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Verifies that Logback events reach the structured JSONL log with secret redaction. */
class StructuredLogbackAppenderTest {
    @TempDir Path temp;

    @Test
    void appenderWritesSanitizedStructuredEvents() {
        StructuredLogger logger = new StructuredLogger(temp.resolve("data"));
        StructuredLogbackAppender appender = new StructuredLogbackAppender(logger);
        appender.start();
        LoggingEvent event =
                new LoggingEvent(
                        getClass().getName(),
                        (Logger) LoggerFactory.getLogger(getClass()),
                        Level.INFO,
                        "token=abc.def.ghi",
                        null,
                        new Object[0]);
        appender.doAppend(event);
        appender.stop();

        String text = OpsFiles.readText(logger.currentFile());
        assertTrue(text.contains("\"level\":\"INFO\""));
        assertTrue(text.contains("[REDACTED]"));
        assertFalse(text.contains("abc.def.ghi"));
    }
}

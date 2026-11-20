/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** {@link SnapshotTag} accepts plain names and rejects path escapes. */
class SnapshotTagTest {

    @Test
    void acceptsPlainNames() {
        assertEquals("daily", new SnapshotTag("daily").value());
        assertEquals("pre-delete-1", new SnapshotTag("pre-delete-1").value());
    }

    @Test
    void rejectsBlankAndTraversal() {
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new SnapshotTag(null);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new SnapshotTag("  ");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new SnapshotTag("../../evil");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new SnapshotTag("a/b");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new SnapshotTag("a\\b");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new SnapshotTag(".hidden");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new SnapshotTag("/abs");
                    }
                });
    }
}

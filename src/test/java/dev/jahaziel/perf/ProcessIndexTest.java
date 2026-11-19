/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.Set;

/** Workspace-to-pid index behavior, with no operating system interaction. */
class ProcessIndexTest {
    @Test
    void addListContainsAndRemove() {
        ProcessIndex index = new ProcessIndex();
        index.add("ws-1", 20L);
        index.add("ws-1", 10L);
        index.add("ws-2", 30L);
        assertEquals(2, index.workspaceCount());
        assertEquals(Arrays.asList(10L, 20L), index.list("ws-1"));
        assertTrue(index.contains("ws-1", 10L));
        assertTrue(index.remove("ws-1", 10L));
        assertFalse(index.remove("ws-1", 10L));
        assertEquals(1, index.list("ws-1").size());
        assertTrue(index.remove("ws-1", 20L));
        assertEquals(1, index.workspaceCount());
        assertTrue(index.list("ws-1").isEmpty());
    }

    @Test
    void duplicateAddIsIdempotent() {
        ProcessIndex index = new ProcessIndex();
        index.add("ws", 7L);
        index.add("ws", 7L);
        assertEquals(1, index.list("ws").size());
    }

    @Test
    void pidsReturnsDefensiveCopy() {
        ProcessIndex index = new ProcessIndex();
        index.add("ws", 7L);
        Set<Long> copy = index.pids("ws");
        copy.clear();
        assertTrue(index.contains("ws", 7L));
        assertTrue(index.pids("missing").isEmpty());
    }

    @Test
    void removeWorkspaceDropsAllPids() {
        ProcessIndex index = new ProcessIndex();
        index.add("ws", 1L);
        index.add("ws", 2L);
        index.removeWorkspace("ws");
        assertEquals(0, index.workspaceCount());
        assertFalse(index.contains("ws", 1L));
    }

    @Test
    void invalidWorkspaceThrows() {
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new ProcessIndex().add("", 1L);
                    }
                });
    }
}

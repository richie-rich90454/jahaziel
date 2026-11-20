/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Behavior of {@link WorkspaceQuota}. */
class WorkspaceQuotaTest {
    @Test
    void storesLimits() {
        WorkspaceQuota quota = new WorkspaceQuota(21474836480L, 1000000L);
        assertEquals(21474836480L, quota.logicalBytes());
        assertEquals(1000000L, quota.inodeLimit());
    }

    @Test
    void negativeLogicalBytesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        new WorkspaceQuota(-1, 1000000L);
                    }
                });
    }

    @Test
    void negativeInodeLimitIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        new WorkspaceQuota(21474836480L, -1);
                    }
                });
    }
}

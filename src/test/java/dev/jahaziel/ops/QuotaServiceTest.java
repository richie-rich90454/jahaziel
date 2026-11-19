/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Behavior of {@link QuotaService}: per-scope limits and 403 decisions. */
class QuotaServiceTest {
    @Test
    void enforcesStorageQuotaWith403() {
        QuotaService quotas = new QuotaService();
        LimitDecision first =
                quotas.reserve("workspace", "ws-1", QuotaService.STORAGE, 60L, 100L);
        assertTrue(first.allowed());
        LimitDecision second =
                quotas.reserve("workspace", "ws-1", QuotaService.STORAGE, 50L, 100L);
        assertFalse(second.allowed());
        assertEquals(LimitDecision.FORBIDDEN, second.statusCode());
        assertTrue(second.reason().contains("quota exceeded"));
        assertEquals(60L, quotas.usage("workspace", "ws-1", QuotaService.STORAGE));
    }

    @Test
    void isolatesUsersWorkspacesAndResources() {
        QuotaService quotas = new QuotaService();
        quotas.reserve("user", "alice", QuotaService.PROCESS, 5L, 10L);
        quotas.reserve("user", "bob", QuotaService.PROCESS, 5L, 10L);
        quotas.reserve("workspace", "ws-1", QuotaService.SNAPSHOT, 30L, 30L);
        assertEquals(5L, quotas.usage("user", "alice", QuotaService.PROCESS));
        assertEquals(5L, quotas.usage("user", "bob", QuotaService.PROCESS));
        assertEquals(30L, quotas.usage("workspace", "ws-1", QuotaService.SNAPSHOT));
        assertEquals(0L, quotas.usage("user", "alice", QuotaService.STORAGE));
    }

    @Test
    void releaseFreesCapacity() {
        QuotaService quotas = new QuotaService();
        quotas.reserve("user", "alice", QuotaService.SESSION, 8L, 10L);
        quotas.release("user", "alice", QuotaService.SESSION, 5L);
        assertEquals(3L, quotas.usage("user", "alice", QuotaService.SESSION));
        quotas.release("user", "alice", QuotaService.SESSION, 100L);
        assertEquals(0L, quotas.usage("user", "alice", QuotaService.SESSION));
    }
}

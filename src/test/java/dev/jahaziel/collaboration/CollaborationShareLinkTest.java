/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class CollaborationShareLinkTest {
    @Test
    void oneTimeTokenIsHashedAndUsedOnce() {
        CollaborationFixture fixture = new CollaborationFixture(1000L);
        ShareStart started =
                fixture.shares.create(
                        CollaborationFixture.WORKSPACE,
                        CollaborationFixture.OWNER,
                        Roles.SCOPE_VIEW,
                        10000L,
                        true,
                        1000L);
        ShareLink stored = fixture.store.shareLink(started.shareId());
        assertFalse(started.token().equals(stored.tokenHash()));
        assertEquals(Roles.SCOPE_VIEW, fixture.shares.redeem(started.token(), 2000L).scope());
        assertTrue(fixture.store.shareLink(started.shareId()).used());
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.shares.redeem(started.token(), 3000L);
                    }
                });
        List<AuditEntry> entries = fixture.store.audit(CollaborationFixture.WORKSPACE);
        boolean redeemed = false;
        for (int index = 0; index < entries.size(); index++) {
            if ("share.redeem".equals(entries.get(index).action())) {
                redeemed = true;
            }
        }
        assertTrue(redeemed);
    }

    @Test
    void expiredTokenIsRejected() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        final ShareStart started =
                fixture.shares.create(
                        CollaborationFixture.WORKSPACE,
                        CollaborationFixture.OWNER,
                        Roles.SCOPE_VIEW,
                        1000L,
                        false,
                        1000L);
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.shares.redeem(started.token(), 2000L);
                    }
                });
    }

    @Test
    void revocationAndScopeRestrictions() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.shares.create(
                                CollaborationFixture.WORKSPACE,
                                CollaborationFixture.VIEWER,
                                Roles.SCOPE_EDIT,
                                10000L,
                                false,
                                1000L);
                    }
                });
        final ShareStart started =
                fixture.shares.create(
                        CollaborationFixture.WORKSPACE,
                        CollaborationFixture.OWNER,
                        Roles.SCOPE_VIEW,
                        10000L,
                        false,
                        1000L);
        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.shares.revoke(
                                CollaborationFixture.WORKSPACE,
                                CollaborationFixture.VIEWER,
                                started.shareId());
                    }
                });
        fixture.shares.revoke(
                CollaborationFixture.WORKSPACE,
                CollaborationFixture.OWNER,
                started.shareId());
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.shares.redeem(started.token(), 2000L);
                    }
                });
    }
}

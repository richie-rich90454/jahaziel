/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

final class CollaborationRoleTest {
    @Test
    void ownerInvitesAndViewerAcceptsWithAudit() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        final String invitee = "invitee-1";
        final Invitation invitation =
                fixture.membership.invite(
                        CollaborationFixture.WORKSPACE,
                        CollaborationFixture.OWNER,
                        invitee,
                        Roles.EDITOR,
                        5000L,
                        1000L);
        assertNotNull(invitation);
        assertEquals(6000L, invitation.expiresAt());

        Member accepted = fixture.membership.accept(invitation.id(), invitee, 2000L);
        assertEquals(Roles.EDITOR, accepted.role());
        assertEquals(
                Roles.EDITOR, fixture.membership.roleOf(CollaborationFixture.WORKSPACE, invitee));

        List<AuditEntry> entries = fixture.store.audit(CollaborationFixture.WORKSPACE);
        boolean invited = false;
        boolean acceptedAudit = false;
        for (int index = 0; index < entries.size(); index++) {
            String action = entries.get(index).action();
            if ("membership.invite".equals(action)) {
                invited = true;
            }
            if ("membership.accept".equals(action)) {
                acceptedAudit = true;
            }
        }
        assertTrue(invited);
        assertTrue(acceptedAudit);
    }

    @Test
    void viewerCannotInvite() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.membership.invite(
                                CollaborationFixture.WORKSPACE,
                                CollaborationFixture.VIEWER,
                                "nobody",
                                Roles.EDITOR,
                                5000L,
                                1000L);
                    }
                });
    }

    @Test
    void expiredInvitationIsRejected() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        final Invitation invitation =
                fixture.membership.invite(
                        CollaborationFixture.WORKSPACE,
                        CollaborationFixture.OWNER,
                        "late-user",
                        Roles.VIEWER,
                        1000L,
                        1000L);
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.membership.accept(invitation.id(), "late-user", 2000L);
                    }
                });
    }

    @Test
    void onlyOwnerChangesRolesAndRemovesMembers() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.membership.changeRole(
                                CollaborationFixture.WORKSPACE,
                                CollaborationFixture.EDITOR,
                                CollaborationFixture.VIEWER,
                                Roles.EDITOR);
                    }
                });
        fixture.membership.changeRole(
                CollaborationFixture.WORKSPACE,
                CollaborationFixture.OWNER,
                CollaborationFixture.VIEWER,
                Roles.EDITOR);
        assertEquals(
                Roles.EDITOR,
                fixture.membership.roleOf(
                        CollaborationFixture.WORKSPACE, CollaborationFixture.VIEWER));

        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.membership.remove(
                                CollaborationFixture.WORKSPACE,
                                CollaborationFixture.OWNER,
                                CollaborationFixture.OWNER);
                    }
                });
        fixture.membership.remove(
                CollaborationFixture.WORKSPACE,
                CollaborationFixture.OWNER,
                CollaborationFixture.EDITOR);
        assertEquals(
                null,
                fixture.membership.roleOf(
                        CollaborationFixture.WORKSPACE, CollaborationFixture.EDITOR));
    }

    @Test
    void ownershipTransferDemotesOldOwner() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        fixture.membership.transferOwnership(
                CollaborationFixture.WORKSPACE,
                CollaborationFixture.OWNER,
                CollaborationFixture.EDITOR);
        assertEquals(
                Roles.OWNER,
                fixture.membership.roleOf(
                        CollaborationFixture.WORKSPACE, CollaborationFixture.EDITOR));
        assertEquals(
                Roles.EDITOR,
                fixture.membership.roleOf(
                        CollaborationFixture.WORKSPACE, CollaborationFixture.OWNER));
        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.membership.invite(
                                CollaborationFixture.WORKSPACE,
                                CollaborationFixture.OWNER,
                                "later",
                                Roles.VIEWER,
                                5000L,
                                1000L);
                    }
                });
    }
}

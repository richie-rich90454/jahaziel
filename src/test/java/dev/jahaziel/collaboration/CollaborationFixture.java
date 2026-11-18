/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.security.Principal;
import java.util.UUID;

/** Wires real services against the fake in-memory store for collaboration tests. */
final class CollaborationFixture {
    static final String WORKSPACE = UUID.randomUUID().toString();
    static final String OWNER = UUID.randomUUID().toString();
    static final String EDITOR = UUID.randomUUID().toString();
    static final String VIEWER = UUID.randomUUID().toString();
    static final String STRANGER = UUID.randomUUID().toString();

    final long now;
    final InMemoryCollaborationStore store = new InMemoryCollaborationStore();
    final CollaborationAudit audit = new CollaborationAudit(store);
    final MembershipService membership = new MembershipService(store, audit);
    final PresenceService presence = new PresenceService();
    final CommentService comments = new CommentService(store, audit, membership);
    final LiveSessionService sessions = new LiveSessionService(store, audit, membership);
    final ShareLinkService shares = new ShareLinkService(store, audit, membership);
    final CollaborationController controller =
            new CollaborationController(membership, presence, comments, sessions, shares, store);

    CollaborationFixture(long now) {
        this.now = now;
        store.putMember(new Member(WORKSPACE, OWNER, Roles.OWNER, now));
        store.putMember(new Member(WORKSPACE, EDITOR, Roles.EDITOR, now));
        store.putMember(new Member(WORKSPACE, VIEWER, Roles.VIEWER, now));
    }

    static Principal principal(final String userId) {
        return new Principal() {
            @Override
            public String getName() {
                return userId;
            }

            @Override
            public int hashCode() {
                return userId.hashCode();
            }

            @Override
            public boolean equals(Object other) {
                if (!(other instanceof Principal)) {
                    return false;
                }
                return userId.equals(((Principal) other).getName());
            }

            @Override
            public String toString() {
                return userId;
            }
        };
    }
}

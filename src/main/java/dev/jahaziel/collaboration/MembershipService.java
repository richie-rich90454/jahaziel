/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owner-only workspace membership mutations. Every mutation is audit-logged. */
@Service
public final class MembershipService {
    private final CollaborationStore store;
    private final CollaborationAudit audit;

    public MembershipService(CollaborationStore store, CollaborationAudit audit) {
        this.store = store;
        this.audit = audit;
    }

    /** Creates an invitation; only the workspace owner may do this. */
    public Invitation invite(
            String workspaceId,
            String actor,
            String invitee,
            String role,
            long ttlMillis,
            long nowMillis) {
        requireOwner(workspaceId, actor);
        if (!Roles.EDITOR.equals(role) && !Roles.VIEWER.equals(role)) {
            throw new IllegalArgumentException("invite role must be EDITOR or VIEWER");
        }
        if (invitee == null || invitee.trim().length() == 0) {
            throw new IllegalArgumentException("invitee is required");
        }
        if (ttlMillis <= 0L) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (store.member(workspaceId, invitee) != null) {
            throw new IllegalArgumentException("invitee is already a member");
        }
        long created = nowMillis;
        Invitation invitation =
                new Invitation(
                        UUID.randomUUID().toString(),
                        workspaceId,
                        invitee,
                        role,
                        actor,
                        created,
                        created + ttlMillis);
        store.putInvitation(invitation);
        Map<String, String> details = new HashMap<String, String>();
        details.put("invitee", invitee);
        details.put("role", role);
        details.put("expiresAt", Long.toString(invitation.expiresAt()));
        audit.record(workspaceId, actor, "membership.invite", invitation.id(), details);
        return invitation;
    }

    /** Accepts a valid invitation and adds the member. */
    public Member accept(String token, String acceptingUser, long nowMillis) {
        Invitation invitation = store.invitation(token);
        if (invitation == null) {
            throw new IllegalArgumentException("unknown invitation");
        }
        if (invitation.accepted()) {
            throw new IllegalArgumentException("invitation already accepted");
        }
        if (nowMillis >= invitation.expiresAt()) {
            throw new IllegalArgumentException("invitation expired");
        }
        if (acceptingUser == null
                || acceptingUser.trim().length() == 0
                || (!invitation.invitee().isEmpty()
                        && !invitation.invitee().equals(acceptingUser))) {
            throw new CollaborationAccessDeniedException("invitation belongs to another user");
        }
        Member member =
                new Member(invitation.workspaceId(), acceptingUser, invitation.role(), nowMillis);
        store.putMember(member);
        invitation.accepted(true);
        invitation.acceptedAt(nowMillis);
        store.putInvitation(invitation);
        Map<String, String> details = new HashMap<String, String>();
        details.put("role", invitation.role());
        audit.record(
                invitation.workspaceId(),
                acceptingUser,
                "membership.accept",
                acceptingUser,
                details);
        return member;
    }

    /** Changes a non-owner member's role; owner-only. */
    public Member changeRole(String workspaceId, String actor, String target, String newRole) {
        requireOwner(workspaceId, actor);
        if (!Roles.isRole(newRole) || Roles.OWNER.equals(newRole)) {
            throw new IllegalArgumentException("new role must be EDITOR or VIEWER");
        }
        Member member = requireMember(workspaceId, target);
        if (Roles.OWNER.equals(member.role())) {
            throw new IllegalArgumentException("use transferOwnership to change the owner");
        }
        member.role(newRole);
        store.putMember(member);
        Map<String, String> details = new HashMap<String, String>();
        details.put("role", newRole);
        audit.record(workspaceId, actor, "membership.changeRole", target, details);
        return member;
    }

    /** Removes a non-owner member; owner-only. */
    public void remove(String workspaceId, String actor, String target) {
        requireOwner(workspaceId, actor);
        Member member = requireMember(workspaceId, target);
        if (Roles.OWNER.equals(member.role())) {
            throw new IllegalArgumentException("the owner cannot be removed");
        }
        store.removeMember(workspaceId, target);
        audit.record(
                workspaceId, actor, "membership.remove", target, new HashMap<String, String>());
    }

    /** Transfers ownership to an existing member; the old owner becomes an editor. */
    public void transferOwnership(String workspaceId, String actor, String newOwner) {
        requireOwner(workspaceId, actor);
        Member incoming = requireMember(workspaceId, newOwner);
        Member outgoing = requireMember(workspaceId, actor);
        outgoing.role(Roles.EDITOR);
        incoming.role(Roles.OWNER);
        store.putMember(outgoing);
        store.putMember(incoming);
        Map<String, String> details = new HashMap<String, String>();
        details.put("from", actor);
        details.put("to", newOwner);
        audit.record(workspaceId, actor, "membership.transferOwnership", newOwner, details);
    }

    /** Returns the role of a user in a workspace, or null when the user is not a member. */
    public String roleOf(String workspaceId, String userId) {
        Member member = store.member(workspaceId, userId);
        return member == null ? null : member.role();
    }

    public List<Member> members(String workspaceId) {
        return store.members(workspaceId);
    }

    /** Authorization check used by every collaboration endpoint. */
    public void requireRole(String workspaceId, String userId, String needed) {
        String role = roleOf(workspaceId, userId);
        if (Roles.rank(role) < Roles.rank(needed)) {
            throw new CollaborationAccessDeniedException(
                    "role " + needed + " is required for workspace " + workspaceId);
        }
    }

    public void requireOwner(String workspaceId, String userId) {
        requireRole(workspaceId, userId, Roles.OWNER);
    }

    private Member requireMember(String workspaceId, String userId) {
        Member member = store.member(workspaceId, userId);
        if (member == null) {
            throw new IllegalArgumentException("user is not a member of this workspace");
        }
        return member;
    }
}

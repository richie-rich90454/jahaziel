/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.util.List;

/** Storage contract for collaboration state. Concrete stores decide where rows live. */
public abstract class CollaborationStore {
    public abstract void putMember(Member member);

    public abstract Member member(String workspaceId, String userId);

    public abstract void removeMember(String workspaceId, String userId);

    public abstract List<Member> members(String workspaceId);

    public abstract void putInvitation(Invitation invitation);

    public abstract Invitation invitation(String invitationId);

    public abstract List<Invitation> invitations(String workspaceId);

    public abstract void putComment(FileComment comment);

    public abstract FileComment comment(String commentId);

    public abstract List<FileComment> comments(String workspaceId);

    public abstract void removeComment(String commentId);

    public abstract void putReply(CommentReply reply);

    public abstract List<CommentReply> replies(String commentId);

    public abstract void putSession(LiveSession session);

    public abstract LiveSession session(String sessionId);

    public abstract LiveSession sessionByHash(String tokenHash);

    public abstract List<LiveSession> sessions(String workspaceId);

    public abstract void removeSession(String sessionId);

    public abstract void putShareLink(ShareLink link);

    public abstract ShareLink shareLink(String shareId);

    public abstract ShareLink shareLinkByHash(String tokenHash);

    public abstract List<ShareLink> shareLinks(String workspaceId);

    public abstract void appendAudit(AuditEntry entry);

    public abstract List<AuditEntry> audit(String workspaceId);
}

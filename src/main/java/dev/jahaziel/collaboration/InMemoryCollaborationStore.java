/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fake in-memory store used by unit tests and local runs. No database required. */
public final class InMemoryCollaborationStore extends CollaborationStore {
    private final Map<String, Member> members = new HashMap<String, Member>();
    private final Map<String, Invitation> invitations = new LinkedHashMap<String, Invitation>();
    private final Map<String, FileComment> comments = new LinkedHashMap<String, FileComment>();
    private final Map<String, CommentReply> replies = new LinkedHashMap<String, CommentReply>();
    private final Map<String, LiveSession> sessions = new LinkedHashMap<String, LiveSession>();
    private final Map<String, ShareLink> shares = new LinkedHashMap<String, ShareLink>();
    private final List<AuditEntry> audit = new ArrayList<AuditEntry>();

    private static String memberKey(String workspaceId, String userId) {
        return workspaceId + "|" + userId;
    }

    @Override
    public synchronized void putMember(Member member) {
        members.put(memberKey(member.workspaceId(), member.userId()), member);
    }

    @Override
    public synchronized Member member(String workspaceId, String userId) {
        return members.get(memberKey(workspaceId, userId));
    }

    @Override
    public synchronized void removeMember(String workspaceId, String userId) {
        members.remove(memberKey(workspaceId, userId));
    }

    @Override
    public synchronized List<Member> members(String workspaceId) {
        List<Member> found = new ArrayList<Member>();
        java.util.Iterator<Member> iterator = members.values().iterator();
        while (iterator.hasNext()) {
            Member candidate = iterator.next();
            if (candidate.workspaceId().equals(workspaceId)) {
                found.add(candidate);
            }
        }
        return found;
    }

    @Override
    public synchronized void putInvitation(Invitation invitation) {
        invitations.put(invitation.id(), invitation);
    }

    @Override
    public synchronized Invitation invitation(String invitationId) {
        return invitations.get(invitationId);
    }

    @Override
    public synchronized List<Invitation> invitations(String workspaceId) {
        List<Invitation> found = new ArrayList<Invitation>();
        java.util.Iterator<Invitation> iterator = invitations.values().iterator();
        while (iterator.hasNext()) {
            Invitation candidate = iterator.next();
            if (candidate.workspaceId().equals(workspaceId)) {
                found.add(candidate);
            }
        }
        return found;
    }

    @Override
    public synchronized void putComment(FileComment comment) {
        comments.put(comment.id(), comment);
    }

    @Override
    public synchronized FileComment comment(String commentId) {
        return comments.get(commentId);
    }

    @Override
    public synchronized List<FileComment> comments(String workspaceId) {
        List<FileComment> found = new ArrayList<FileComment>();
        java.util.Iterator<FileComment> iterator = comments.values().iterator();
        while (iterator.hasNext()) {
            FileComment candidate = iterator.next();
            if (candidate.workspaceId().equals(workspaceId)) {
                found.add(candidate);
            }
        }
        return found;
    }

    @Override
    public synchronized void removeComment(String commentId) {
        comments.remove(commentId);
    }

    @Override
    public synchronized void putReply(CommentReply reply) {
        replies.put(reply.id(), reply);
    }

    @Override
    public synchronized List<CommentReply> replies(String commentId) {
        List<CommentReply> found = new ArrayList<CommentReply>();
        java.util.Iterator<CommentReply> iterator = replies.values().iterator();
        while (iterator.hasNext()) {
            CommentReply candidate = iterator.next();
            if (candidate.commentId().equals(commentId)) {
                found.add(candidate);
            }
        }
        return found;
    }

    @Override
    public synchronized void putSession(LiveSession session) {
        sessions.put(session.id(), session);
    }

    @Override
    public synchronized LiveSession session(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public synchronized LiveSession sessionByHash(String tokenHash) {
        java.util.Iterator<LiveSession> iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            LiveSession candidate = iterator.next();
            if (candidate.tokenHash().equals(tokenHash)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public synchronized List<LiveSession> sessions(String workspaceId) {
        List<LiveSession> found = new ArrayList<LiveSession>();
        java.util.Iterator<LiveSession> iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            LiveSession candidate = iterator.next();
            if (candidate.workspaceId().equals(workspaceId)) {
                found.add(candidate);
            }
        }
        return found;
    }

    @Override
    public synchronized void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public synchronized void putShareLink(ShareLink link) {
        shares.put(link.id(), link);
    }

    @Override
    public synchronized ShareLink shareLink(String shareId) {
        return shares.get(shareId);
    }

    @Override
    public synchronized ShareLink shareLinkByHash(String tokenHash) {
        java.util.Iterator<ShareLink> iterator = shares.values().iterator();
        while (iterator.hasNext()) {
            ShareLink candidate = iterator.next();
            if (candidate.tokenHash().equals(tokenHash)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public synchronized List<ShareLink> shareLinks(String workspaceId) {
        List<ShareLink> found = new ArrayList<ShareLink>();
        java.util.Iterator<ShareLink> iterator = shares.values().iterator();
        while (iterator.hasNext()) {
            ShareLink candidate = iterator.next();
            if (candidate.workspaceId().equals(workspaceId)) {
                found.add(candidate);
            }
        }
        return found;
    }

    @Override
    public synchronized void appendAudit(AuditEntry entry) {
        audit.add(entry);
    }

    @Override
    public synchronized List<AuditEntry> audit(String workspaceId) {
        List<AuditEntry> found = new ArrayList<AuditEntry>();
        for (int index = 0; index < audit.size(); index++) {
            AuditEntry candidate = audit.get(index);
            if (candidate.workspaceId().equals(workspaceId)) {
                found.add(candidate);
            }
        }
        return found;
    }
}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** File comments and replies with workspace path-containment validation. */
@Service
public final class CommentService {
    private final CollaborationStore store;
    private final CollaborationAudit audit;
    private final MembershipService membership;

    public CommentService(
            CollaborationStore store, CollaborationAudit audit, MembershipService membership) {
        this.store = store;
        this.audit = audit;
        this.membership = membership;
    }

    public FileComment create(
            String workspaceId,
            String author,
            String path,
            int startLine,
            int endLine,
            String body,
            long nowMillis) {
        membership.requireRole(workspaceId, author, Roles.EDITOR);
        String contained = WorkspacePaths.validate(path);
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("invalid line range");
        }
        if (body == null || body.trim().length() == 0) {
            throw new IllegalArgumentException("comment body is required");
        }
        FileComment comment =
                new FileComment(
                        UUID.randomUUID().toString(),
                        workspaceId,
                        contained,
                        startLine,
                        endLine,
                        author,
                        body,
                        nowMillis);
        store.putComment(comment);
        Map<String, String> details = new HashMap<String, String>();
        details.put("path", contained);
        details.put("lines", startLine + "-" + endLine);
        audit.record(workspaceId, author, "comment.create", comment.id(), details);
        return comment;
    }

    public List<FileComment> list(String workspaceId, String path) {
        List<FileComment> all = store.comments(workspaceId);
        if (path == null) {
            return all;
        }
        String wanted = WorkspacePaths.validate(path);
        List<FileComment> found = new ArrayList<FileComment>();
        for (int index = 0; index < all.size(); index++) {
            FileComment candidate = all.get(index);
            if (candidate.path().equals(wanted)) {
                found.add(candidate);
            }
        }
        return found;
    }

    public CommentReply reply(
            String workspaceId, String author, String commentId, String body, long nowMillis) {
        membership.requireRole(workspaceId, author, Roles.EDITOR);
        FileComment comment = requireComment(workspaceId, commentId);
        if (body == null || body.trim().length() == 0) {
            throw new IllegalArgumentException("reply body is required");
        }
        CommentReply reply =
                new CommentReply(
                        UUID.randomUUID().toString(), commentId, author, body, nowMillis);
        store.putReply(reply);
        audit.record(workspaceId, author, "comment.reply", commentId, new HashMap<String, String>());
        return reply;
    }

    /** Only the author or the workspace owner may resolve a comment. */
    public FileComment resolve(
            String workspaceId, String author, String commentId, boolean resolved, long nowMillis) {
        FileComment comment = requireComment(workspaceId, commentId);
        requireAuthorOrOwner(workspaceId, author, comment);
        comment.resolved(resolved);
        comment.updatedAt(nowMillis);
        store.putComment(comment);
        Map<String, String> details = new HashMap<String, String>();
        details.put("resolved", Boolean.toString(resolved));
        audit.record(workspaceId, author, "comment.resolve", commentId, details);
        return comment;
    }

    /** Only the author or the workspace owner may delete a comment. */
    public void delete(String workspaceId, String actor, String commentId) {
        FileComment comment = requireComment(workspaceId, commentId);
        requireAuthorOrOwner(workspaceId, actor, comment);
        store.removeComment(commentId);
        audit.record(workspaceId, actor, "comment.delete", commentId, new HashMap<String, String>());
    }

    public List<CommentReply> replies(String workspaceId, String commentId) {
        requireComment(workspaceId, commentId);
        return store.replies(commentId);
    }

    private void requireAuthorOrOwner(String workspaceId, String actor, FileComment comment) {
        boolean owner = Roles.OWNER.equals(membership.roleOf(workspaceId, actor));
        if (!owner && !comment.author().equals(actor)) {
            throw new CollaborationAccessDeniedException("only the author or owner may change a comment");
        }
    }

    private FileComment requireComment(String workspaceId, String commentId) {
        FileComment comment = store.comment(commentId);
        if (comment == null || !comment.workspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("unknown comment in this workspace");
        }
        return comment;
    }
}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/** Database-backed collaboration store. All ids and timestamps are stored as plain scalars. */
@Repository
public final class JdbcCollaborationStore extends CollaborationStore {
    private static final RowMapper<Member> MEMBER_ROW =
            new RowMapper<Member>() {
                @Override
                public Member mapRow(ResultSet row, int index) throws SQLException {
                    return new Member(
                            row.getString("workspace_id"),
                            row.getString("user_id"),
                            row.getString("role"),
                            row.getLong("joined_at"));
                }
            };

    private static final RowMapper<Invitation> INVITATION_ROW =
            new RowMapper<Invitation>() {
                @Override
                public Invitation mapRow(ResultSet row, int index) throws SQLException {
                    Invitation invitation =
                            new Invitation(
                                    row.getString("invitation_id"),
                                    row.getString("workspace_id"),
                                    row.getString("invitee"),
                                    row.getString("role"),
                                    row.getString("invited_by"),
                                    row.getLong("created_at"),
                                    row.getLong("expires_at"));
                    invitation.accepted(row.getInt("accepted") == 1);
                    long acceptedAt = row.getLong("accepted_at");
                    invitation.acceptedAt(row.wasNull() ? 0L : acceptedAt);
                    return invitation;
                }
            };

    private static final RowMapper<FileComment> COMMENT_ROW =
            new RowMapper<FileComment>() {
                @Override
                public FileComment mapRow(ResultSet row, int index) throws SQLException {
                    FileComment comment =
                            new FileComment(
                                    row.getString("comment_id"),
                                    row.getString("workspace_id"),
                                    row.getString("path"),
                                    row.getInt("start_line"),
                                    row.getInt("end_line"),
                                    row.getString("author"),
                                    row.getString("body"),
                                    row.getLong("created_at"));
                    comment.resolved(row.getInt("resolved") == 1);
                    comment.updatedAt(row.getLong("updated_at"));
                    return comment;
                }
            };

    private static final RowMapper<CommentReply> REPLY_ROW =
            new RowMapper<CommentReply>() {
                @Override
                public CommentReply mapRow(ResultSet row, int index) throws SQLException {
                    return new CommentReply(
                            row.getString("reply_id"),
                            row.getString("comment_id"),
                            row.getString("author"),
                            row.getString("body"),
                            row.getLong("created_at"));
                }
            };

    private static final RowMapper<LiveSession> SESSION_ROW =
            new RowMapper<LiveSession>() {
                @Override
                public LiveSession mapRow(ResultSet row, int index) throws SQLException {
                    LiveSession session =
                            new LiveSession(
                                    row.getString("session_id"),
                                    row.getString("workspace_id"),
                                    row.getString("controller"),
                                    row.getString("token_hash"),
                                    row.getLong("created_at"));
                    session.active(row.getInt("active") == 1);
                    return session;
                }
            };

    private static final RowMapper<String> OBSERVER_ROW =
            new RowMapper<String>() {
                @Override
                public String mapRow(ResultSet row, int index) throws SQLException {
                    return row.getString("user_id");
                }
            };

    private static final RowMapper<ShareLink> SHARE_ROW =
            new RowMapper<ShareLink>() {
                @Override
                public ShareLink mapRow(ResultSet row, int index) throws SQLException {
                    ShareLink link =
                            new ShareLink(
                                    row.getString("share_id"),
                                    row.getString("workspace_id"),
                                    row.getString("token_hash"),
                                    row.getString("scope"),
                                    row.getInt("one_time") == 1,
                                    row.getLong("expires_at"),
                                    row.getString("created_by"),
                                    row.getLong("created_at"));
                    link.used(row.getInt("used") == 1);
                    link.revoked(row.getInt("revoked") == 1);
                    return link;
                }
            };

    private static final RowMapper<AuditEntry> AUDIT_ROW =
            new RowMapper<AuditEntry>() {
                @Override
                public AuditEntry mapRow(ResultSet row, int index) throws SQLException {
                    return new AuditEntry(
                            row.getString("audit_id"),
                            row.getString("workspace_id"),
                            row.getString("actor"),
                            row.getString("action"),
                            row.getString("target"),
                            row.getLong("created_at"),
                            decodeDetails(row.getString("details")));
                }
            };

    private final JdbcTemplate jdbc;

    public JdbcCollaborationStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void putMember(Member member) {
        jdbc.update(
                "DELETE FROM workspace_member WHERE workspace_id = ? AND user_id = ?",
                member.workspaceId(),
                member.userId());
        jdbc.update(
                "INSERT INTO workspace_member (workspace_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)",
                member.workspaceId(),
                member.userId(),
                member.role(),
                member.joinedAt());
    }

    @Override
    public Member member(String workspaceId, String userId) {
        List<Member> found =
                jdbc.query(
                        "SELECT * FROM workspace_member WHERE workspace_id = ? AND user_id = ?",
                        MEMBER_ROW,
                        workspaceId,
                        userId);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public void removeMember(String workspaceId, String userId) {
        jdbc.update(
                "DELETE FROM workspace_member WHERE workspace_id = ? AND user_id = ?",
                workspaceId,
                userId);
    }

    @Override
    public List<Member> members(String workspaceId) {
        return jdbc.query(
                "SELECT * FROM workspace_member WHERE workspace_id = ?", MEMBER_ROW, workspaceId);
    }

    @Override
    public void putInvitation(Invitation invitation) {
        jdbc.update("DELETE FROM workspace_invitation WHERE invitation_id = ?", invitation.id());
        jdbc.update(
                "INSERT INTO workspace_invitation (invitation_id, workspace_id, invitee, role, invited_by, created_at, expires_at, accepted, accepted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                invitation.id(),
                invitation.workspaceId(),
                invitation.invitee(),
                invitation.role(),
                invitation.invitedBy(),
                invitation.createdAt(),
                invitation.expiresAt(),
                flag(invitation.accepted()),
                invitation.acceptedAt() == 0L ? null : invitation.acceptedAt());
    }

    @Override
    public Invitation invitation(String invitationId) {
        List<Invitation> found =
                jdbc.query(
                        "SELECT * FROM workspace_invitation WHERE invitation_id = ?",
                        INVITATION_ROW,
                        invitationId);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public List<Invitation> invitations(String workspaceId) {
        return jdbc.query(
                "SELECT * FROM workspace_invitation WHERE workspace_id = ?",
                INVITATION_ROW,
                workspaceId);
    }

    @Override
    public void putComment(FileComment comment) {
        jdbc.update("DELETE FROM file_comment WHERE comment_id = ?", comment.id());
        jdbc.update(
                "INSERT INTO file_comment (comment_id, workspace_id, path, start_line, end_line, author, body, resolved, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                comment.id(),
                comment.workspaceId(),
                comment.path(),
                comment.startLine(),
                comment.endLine(),
                comment.author(),
                comment.body(),
                flag(comment.resolved()),
                comment.createdAt(),
                comment.updatedAt());
    }

    @Override
    public FileComment comment(String commentId) {
        List<FileComment> found =
                jdbc.query(
                        "SELECT * FROM file_comment WHERE comment_id = ?", COMMENT_ROW, commentId);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public List<FileComment> comments(String workspaceId) {
        return jdbc.query(
                "SELECT * FROM file_comment WHERE workspace_id = ?", COMMENT_ROW, workspaceId);
    }

    @Override
    public void removeComment(String commentId) {
        jdbc.update("DELETE FROM file_comment WHERE comment_id = ?", commentId);
        jdbc.update("DELETE FROM file_comment_reply WHERE comment_id = ?", commentId);
    }

    @Override
    public void putReply(CommentReply reply) {
        jdbc.update("DELETE FROM file_comment_reply WHERE reply_id = ?", reply.id());
        jdbc.update(
                "INSERT INTO file_comment_reply (reply_id, comment_id, author, body, created_at) VALUES (?, ?, ?, ?, ?)",
                reply.id(),
                reply.commentId(),
                reply.author(),
                reply.body(),
                reply.createdAt());
    }

    @Override
    public List<CommentReply> replies(String commentId) {
        return jdbc.query(
                "SELECT * FROM file_comment_reply WHERE comment_id = ?", REPLY_ROW, commentId);
    }

    @Override
    public void putSession(LiveSession session) {
        jdbc.update("DELETE FROM live_session WHERE session_id = ?", session.id());
        jdbc.update(
                "INSERT INTO live_session (session_id, workspace_id, controller, token_hash, active, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                session.id(),
                session.workspaceId(),
                session.controller(),
                session.tokenHash(),
                flag(session.active()),
                session.createdAt());
        jdbc.update("DELETE FROM live_session_observer WHERE session_id = ?", session.id());
        List<String> observers = session.observers();
        for (int index = 0; index < observers.size(); index++) {
            jdbc.update(
                    "INSERT INTO live_session_observer (session_id, user_id, consent_at) VALUES (?, ?, ?)",
                    session.id(),
                    observers.get(index),
                    session.createdAt());
        }
    }

    @Override
    public LiveSession session(String sessionId) {
        List<LiveSession> found =
                jdbc.query(
                        "SELECT * FROM live_session WHERE session_id = ?", SESSION_ROW, sessionId);
        if (found.isEmpty()) {
            return null;
        }
        LiveSession session = found.get(0);
        List<String> observers =
                jdbc.query(
                        "SELECT user_id FROM live_session_observer WHERE session_id = ?",
                        OBSERVER_ROW,
                        sessionId);
        for (int index = 0; index < observers.size(); index++) {
            session.addObserver(observers.get(index));
        }
        return session;
    }

    @Override
    public LiveSession sessionByHash(String tokenHash) {
        List<LiveSession> found =
                jdbc.query(
                        "SELECT * FROM live_session WHERE token_hash = ?", SESSION_ROW, tokenHash);
        if (found.isEmpty()) {
            return null;
        }
        LiveSession session = found.get(0);
        List<String> observers =
                jdbc.query(
                        "SELECT user_id FROM live_session_observer WHERE session_id = ?",
                        OBSERVER_ROW,
                        session.id());
        for (int index = 0; index < observers.size(); index++) {
            session.addObserver(observers.get(index));
        }
        return session;
    }

    @Override
    public List<LiveSession> sessions(String workspaceId) {
        return jdbc.query(
                "SELECT * FROM live_session WHERE workspace_id = ?", SESSION_ROW, workspaceId);
    }

    @Override
    public void removeSession(String sessionId) {
        jdbc.update("DELETE FROM live_session_observer WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM live_session WHERE session_id = ?", sessionId);
    }

    @Override
    public void putShareLink(ShareLink link) {
        jdbc.update("DELETE FROM share_link WHERE share_id = ?", link.id());
        jdbc.update(
                "INSERT INTO share_link (share_id, workspace_id, token_hash, scope, one_time, used, revoked, expires_at, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                link.id(),
                link.workspaceId(),
                link.tokenHash(),
                link.scope(),
                flag(link.oneTime()),
                flag(link.used()),
                flag(link.revoked()),
                link.expiresAt(),
                link.createdBy(),
                link.createdAt());
    }

    @Override
    public ShareLink shareLink(String shareId) {
        List<ShareLink> found =
                jdbc.query("SELECT * FROM share_link WHERE share_id = ?", SHARE_ROW, shareId);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public ShareLink shareLinkByHash(String tokenHash) {
        List<ShareLink> found =
                jdbc.query("SELECT * FROM share_link WHERE token_hash = ?", SHARE_ROW, tokenHash);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public List<ShareLink> shareLinks(String workspaceId) {
        return jdbc.query(
                "SELECT * FROM share_link WHERE workspace_id = ?", SHARE_ROW, workspaceId);
    }

    @Override
    public void appendAudit(AuditEntry entry) {
        jdbc.update(
                "INSERT INTO collaboration_audit (audit_id, workspace_id, actor, action, target, details, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                entry.id(),
                entry.workspaceId(),
                entry.actor(),
                entry.action(),
                entry.target(),
                encodeDetails(entry.details()),
                entry.createdAt());
    }

    @Override
    public List<AuditEntry> audit(String workspaceId) {
        return jdbc.query(
                "SELECT * FROM collaboration_audit WHERE workspace_id = ?", AUDIT_ROW, workspaceId);
    }

    private static int flag(boolean value) {
        return value ? 1 : 0;
    }

    private static String encodeDetails(Map<String, String> details) {
        StringBuilder text = new StringBuilder();
        Iterator<Map.Entry<String, String>> iterator = details.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            text.append(entry.getKey()).append('=').append(entry.getValue()).append(';');
        }
        return text.toString();
    }

    private static Map<String, String> decodeDetails(String raw) {
        Map<String, String> details = new HashMap<String, String>();
        if (raw == null || raw.length() == 0) {
            return details;
        }
        String[] parts = raw.split(";");
        for (int index = 0; index < parts.length; index++) {
            int split = parts[index].indexOf('=');
            if (split > 0) {
                details.put(parts[index].substring(0, split), parts[index].substring(split + 1));
            }
        }
        return details;
    }
}

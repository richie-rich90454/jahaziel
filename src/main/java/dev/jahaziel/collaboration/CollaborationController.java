/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Collaboration endpoints. Every endpoint checks the caller's workspace role first. */
@RestController
@RequestMapping("/api/v1/workspaces/{id}")
public final class CollaborationController {
    private final MembershipService membership;
    private final PresenceService presence;
    private final CommentService comments;
    private final LiveSessionService sessions;
    private final ShareLinkService shares;
    private final CollaborationStore store;

    public CollaborationController(
            MembershipService membership,
            PresenceService presence,
            CommentService comments,
            LiveSessionService sessions,
            ShareLinkService shares,
            CollaborationStore store) {
        this.membership = membership;
        this.presence = presence;
        this.comments = comments;
        this.sessions = sessions;
        this.shares = shares;
        this.store = store;
    }

    @GetMapping("/members")
    public List<String> members(@PathVariable UUID id, Principal principal) {
        require(id, principal, Roles.VIEWER);
        List<Member> found = membership.members(id.toString());
        List<String> result = new ArrayList<String>();
        for (int index = 0; index < found.size(); index++) {
            result.add(found.get(index).userId() + ":" + found.get(index).role());
        }
        return result;
    }

    @PostMapping("/members/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> invite(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.OWNER);
        Invitation invitation =
                membership.invite(
                        id.toString(),
                        actor,
                        value(body, "invitee"),
                        value(body, "role"),
                        86400000L,
                        now());
        return one("invitationId", invitation.id());
    }

    @PostMapping("/invitations/{token}/accept")
    public Map<String, String> accept(
            @PathVariable UUID id, @PathVariable String token, Principal principal) {
        String actor = userId(principal);
        Invitation invitation = store.invitation(token);
        if (invitation == null || !invitation.workspaceId().equals(id.toString())) {
            throw new CollaborationAccessDeniedException("invitation is not for this workspace");
        }
        Member member = membership.accept(token, actor, now());
        return one("userId", member.userId());
    }

    @PostMapping("/members/{target}/role")
    public Map<String, String> changeRole(
            @PathVariable UUID id,
            @PathVariable String target,
            Principal principal,
            @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.OWNER);
        Member member = membership.changeRole(id.toString(), actor, target, value(body, "role"));
        return one("role", member.role());
    }

    @DeleteMapping("/members/{target}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id, @PathVariable String target, Principal principal) {
        String actor = require(id, principal, Roles.OWNER);
        membership.remove(id.toString(), actor, target);
    }

    @PostMapping("/owner")
    public Map<String, String> transfer(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.OWNER);
        String newOwner = value(body, "newOwner");
        membership.transferOwnership(id.toString(), actor, newOwner);
        return one("owner", newOwner);
    }

    @PostMapping("/presence/join")
    public Map<String, String> join(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.VIEWER);
        PresenceService.Presence entry = presence.join(id.toString(), actor, now());
        return one("userId", entry.userId());
    }

    @PostMapping("/presence/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.VIEWER);
        presence.leave(id.toString(), actor);
    }

    @PostMapping("/presence/file")
    public Map<String, String> activeFile(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.VIEWER);
        PresenceService.Presence entry =
                presence.activeFile(id.toString(), actor, value(body, "path"), now());
        return one("path", entry.activeFile());
    }

    @PostMapping("/presence/cursor")
    public Map<String, String> cursor(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.VIEWER);
        PresenceService.Presence entry =
                presence.cursor(
                        id.toString(),
                        actor,
                        integer(body, "line"),
                        integer(body, "column"),
                        now());
        return one("userId", entry.userId());
    }

    @GetMapping("/presence")
    public List<String> active(@PathVariable UUID id, Principal principal) {
        require(id, principal, Roles.VIEWER);
        List<PresenceService.Presence> found = presence.active(id.toString(), now());
        List<String> result = new ArrayList<String>();
        for (int index = 0; index < found.size(); index++) {
            result.add(found.get(index).userId());
        }
        return result;
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createComment(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.EDITOR);
        FileComment comment =
                comments.create(
                        id.toString(),
                        actor,
                        value(body, "path"),
                        integer(body, "startLine"),
                        integer(body, "endLine"),
                        value(body, "body"),
                        now());
        return one("commentId", comment.id());
    }

    @GetMapping("/comments")
    public List<String> listComments(
            @PathVariable UUID id, Principal principal, @RequestBody(required = false) Map<String, String> body) {
        require(id, principal, Roles.VIEWER);
        List<FileComment> found = comments.list(id.toString(), value(body, "path"));
        List<String> result = new ArrayList<String>();
        for (int index = 0; index < found.size(); index++) {
            result.add(found.get(index).id() + ":" + found.get(index).path());
        }
        return result;
    }

    @PostMapping("/comments/{commentId}/reply")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> reply(
            @PathVariable UUID id,
            @PathVariable String commentId,
            Principal principal,
            @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.EDITOR);
        CommentReply reply =
                comments.reply(id.toString(), actor, commentId, value(body, "body"), now());
        return one("replyId", reply.id());
    }

    @PostMapping("/comments/{commentId}/resolve")
    public Map<String, String> resolve(
            @PathVariable UUID id,
            @PathVariable String commentId,
            Principal principal,
            @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.VIEWER);
        FileComment comment =
                comments.resolve(
                        id.toString(), actor, commentId, bool(body, "resolved"), now());
        return one("resolved", Boolean.toString(comment.resolved()));
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable UUID id, @PathVariable String commentId, Principal principal) {
        String actor = require(id, principal, Roles.VIEWER);
        comments.delete(id.toString(), actor, commentId);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> startSession(
            @PathVariable UUID id, Principal principal) {
        String actor = require(id, principal, Roles.EDITOR);
        SessionStart started = sessions.create(id.toString(), actor, now());
        Map<String, String> result = new HashMap<String, String>();
        result.put("sessionId", started.sessionId());
        result.put("token", started.token());
        return result;
    }

    @PostMapping("/sessions/{sessionId}/observers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> observe(
            @PathVariable UUID id,
            @PathVariable String sessionId,
            Principal principal,
            @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.VIEWER);
        sessions.joinAsObserver(sessionId, actor, bool(body, "consent"), now());
        return one("observer", actor);
    }

    @DeleteMapping("/sessions/{sessionId}/observers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unobserve(
            @PathVariable UUID id, @PathVariable String sessionId, Principal principal) {
        String actor = require(id, principal, Roles.VIEWER);
        sessions.leaveObserver(sessionId, actor);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(
            @PathVariable UUID id, @PathVariable String sessionId, Principal principal) {
        String actor = require(id, principal, Roles.VIEWER);
        sessions.disconnectController(sessionId, actor);
    }

    @PostMapping("/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createShare(
            @PathVariable UUID id, Principal principal, @RequestBody Map<String, String> body) {
        String actor = require(id, principal, Roles.VIEWER);
        ShareStart started =
                shares.create(
                        id.toString(),
                        actor,
                        value(body, "scope"),
                        86400000L,
                        bool(body, "oneTime"),
                        now());
        Map<String, String> result = new HashMap<String, String>();
        result.put("shareId", started.shareId());
        result.put("token", started.token());
        return result;
    }

    @GetMapping("/shares")
    public List<String> listShares(@PathVariable UUID id, Principal principal) {
        require(id, principal, Roles.VIEWER);
        List<ShareLink> found = shares.list(id.toString());
        List<String> result = new ArrayList<String>();
        for (int index = 0; index < found.size(); index++) {
            result.add(found.get(index).id());
        }
        return result;
    }

    @DeleteMapping("/shares/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShare(
            @PathVariable UUID id, @PathVariable String shareId, Principal principal) {
        String actor = require(id, principal, Roles.OWNER);
        shares.revoke(id.toString(), actor, shareId);
    }

    @GetMapping("/audit")
    public List<String> audit(@PathVariable UUID id, Principal principal) {
        require(id, principal, Roles.OWNER);
        List<AuditEntry> found = store.audit(id.toString());
        List<String> result = new ArrayList<String>();
        for (int index = 0; index < found.size(); index++) {
            result.add(found.get(index).action() + ":" + found.get(index).actor());
        }
        return result;
    }

    @ExceptionHandler(CollaborationAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> denied(CollaborationAccessDeniedException failure) {
        return one("error", failure.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException failure) {
        return one("error", failure.getMessage());
    }

    private String require(UUID id, Principal principal, String role) {
        String actor = userId(principal);
        membership.requireRole(id.toString(), actor, role);
        return actor;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static String value(Map<String, String> body, String key) {
        return body == null ? null : body.get(key);
    }

    private static int integer(Map<String, String> body, String key) {
        String raw = value(body, key);
        if (raw == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
    }

    private static boolean bool(Map<String, String> body, String key) {
        return Boolean.parseBoolean(value(body, key));
    }

    private static Map<String, String> one(String key, String value) {
        Map<String, String> result = new HashMap<String, String>();
        result.put(key, value);
        return result;
    }

    private static String userId(Principal principal) {
        if (principal == null) {
            throw new CollaborationAccessDeniedException("principal is required");
        }
        try {
            return UUID.fromString(principal.getName()).toString();
        } catch (IllegalArgumentException failure) {
            throw new CollaborationAccessDeniedException("principal is not a user id");
        }
    }
}

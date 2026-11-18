/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Persisted, hash-only share links with scope, expiry, one-time use, and revocation. */
@Service
public final class ShareLinkService {
    private static final String TOKEN_PREFIX = "jsl_";

    private final CollaborationStore store;
    private final CollaborationAudit audit;
    private final MembershipService membership;

    public ShareLinkService(
            CollaborationStore store, CollaborationAudit audit, MembershipService membership) {
        this.store = store;
        this.audit = audit;
        this.membership = membership;
    }

    public ShareStart create(
            String workspaceId,
            String actor,
            String scope,
            long ttlMillis,
            boolean oneTime,
            long nowMillis) {
        if (!Roles.isScope(scope)) {
            throw new IllegalArgumentException("share scope must be VIEW or EDIT");
        }
        membership.requireRole(
                workspaceId, actor, Roles.SCOPE_EDIT.equals(scope) ? Roles.EDITOR : Roles.VIEWER);
        if (ttlMillis <= 0L) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        String token = Tokens.generate(TOKEN_PREFIX);
        ShareLink link =
                new ShareLink(
                        UUID.randomUUID().toString(),
                        workspaceId,
                        Tokens.hash(token),
                        scope,
                        oneTime,
                        nowMillis + ttlMillis,
                        actor,
                        nowMillis);
        store.putShareLink(link);
        Map<String, String> details = new HashMap<String, String>();
        details.put("scope", scope);
        details.put("oneTime", Boolean.toString(oneTime));
        audit.record(workspaceId, actor, "share.create", link.id(), details);
        return new ShareStart(link.id(), token);
    }

    /** Redeems a token once, enforcing revocation, expiry, and one-time use. */
    public ShareLink redeem(String token, long nowMillis) {
        ShareLink link = store.shareLinkByHash(Tokens.hash(token));
        if (link == null) {
            throw new IllegalArgumentException("unknown share link");
        }
        if (link.revoked()) {
            throw new IllegalArgumentException("share link is revoked");
        }
        if (link.used()) {
            throw new IllegalArgumentException("share link was already used");
        }
        if (nowMillis >= link.expiresAt()) {
            throw new IllegalArgumentException("share link expired");
        }
        if (link.oneTime()) {
            link.used(true);
            store.putShareLink(link);
        }
        Map<String, String> details = new HashMap<String, String>();
        details.put("scope", link.scope());
        audit.record(
                link.workspaceId(), link.createdBy(), "share.redeem", link.id(), details);
        return link;
    }

    public void revoke(String workspaceId, String actor, String shareId) {
        membership.requireOwner(workspaceId, actor);
        ShareLink link = store.shareLink(shareId);
        if (link == null || !link.workspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("unknown share link in this workspace");
        }
        link.revoked(true);
        store.putShareLink(link);
        audit.record(workspaceId, actor, "share.revoke", shareId, new HashMap<String, String>());
    }

    public List<ShareLink> list(String workspaceId) {
        return store.shareLinks(workspaceId);
    }
}

/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.nio.file.Path;

/** Plain listener that turns ops lifecycle events into signed webhook outbox entries. */
public final class OpsWebhookListener {
    private final OutboundWebhooks hooks;
    private final String targetUrl;
    private final String secret;

    /**
     * Creates a listener. An empty target URL disables emission, leaving the app usable when no
     * webhook endpoint is configured.
     */
    public OpsWebhookListener(OutboundWebhooks hooks, String targetUrl, String secret) {
        this.hooks = hooks;
        this.targetUrl = targetUrl == null ? "" : targetUrl.trim();
        this.secret = secret == null ? "" : secret;
    }

    /** Returns whether events will be enqueued. */
    public boolean enabled() {
        return hooks != null && !targetUrl.isEmpty();
    }

    /** Records a successful backup. */
    public void backupSucceeded(Path archive) {
        String name = archive == null ? "" : archive.getFileName().toString();
        emit("backup.succeeded", "{\"archive\":\"" + OpsFiles.jsonEscape(name) + "\"}");
    }

    /** Records a failed backup with a sanitized reason. */
    public void backupFailed(String reason) {
        emit(
                "backup.failed",
                "{\"reason\":\"" + OpsFiles.jsonEscape(OpsFiles.sanitizeText(reason)) + "\"}");
    }

    /** Records an approval decision. */
    public void approvalDecided(ApprovalQueue.Decision decision) {
        if (decision == null) {
            return;
        }
        StringBuilder payload = new StringBuilder("{\"requestId\":\"");
        payload.append(OpsFiles.jsonEscape(decision.requestId())).append('"');
        payload.append(",\"action\":\"").append(OpsFiles.jsonEscape(decision.action())).append('"');
        payload.append(",\"approver\":\"")
                .append(OpsFiles.jsonEscape(decision.approver()))
                .append('"');
        payload.append(",\"reason\":\"")
                .append(OpsFiles.jsonEscape(OpsFiles.sanitizeText(decision.reason())))
                .append("\"}");
        emit("approval.decided", payload.toString());
    }

    private void emit(String eventType, String payload) {
        if (!enabled()) {
            return;
        }
        hooks.enqueue(new OutboundWebhooks.WebhookEvent(targetUrl, eventType, payload, secret));
    }
}

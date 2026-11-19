/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/** Verifies that listener events reach the outbox with valid HMAC signatures. */
class OpsWebhookListenerTest {
    @TempDir Path temp;

    private static final class CapturingTransport extends OutboundWebhooks.WebhookTransport {
        private String url;
        private String body;
        private String signature;

        @Override
        public int send(String url, String body, String signatureHeader) {
            this.url = url;
            this.body = body;
            this.signature = signatureHeader;
            return 200;
        }
    }

    @Test
    void backupSuccessIsSigned() throws Exception {
        OutboundWebhooks hooks = new OutboundWebhooks(temp.resolve("data"));
        OpsWebhookListener listener =
                new OpsWebhookListener(hooks, "https://hooks.example.test/ops", "shared-secret");
        listener.backupSucceeded(Paths.get("backup-1.zip"));
        assertEquals(1, hooks.pendingCount());

        CapturingTransport transport = new CapturingTransport();
        assertEquals(1, hooks.deliverAll(transport, 3, Duration.ZERO).delivered());
        assertEquals("https://hooks.example.test/ops", transport.url);
        assertTrue(transport.body.contains("\"type\":\"backup.succeeded\""));
        assertTrue(transport.body.contains("backup-1.zip"));
        assertEquals(OutboundWebhooks.sign("shared-secret", transport.body), transport.signature);
    }

    @Test
    void approvalDecisionIsSigned() throws Exception {
        OutboundWebhooks hooks = new OutboundWebhooks(temp.resolve("data"));
        OpsWebhookListener listener =
                new OpsWebhookListener(hooks, "https://hooks.example.test/ops", "shared-secret");
        listener.approvalDecided(new ApprovalQueue.Decision("req-1", "APPROVED", "admin", "ok"));
        assertEquals(1, hooks.pendingCount());

        CapturingTransport transport = new CapturingTransport();
        assertEquals(1, hooks.deliverAll(transport, 3, Duration.ZERO).delivered());
        assertTrue(transport.body.contains("\"type\":\"approval.decided\""));
        assertTrue(transport.body.contains("req-1"));
        assertEquals(OutboundWebhooks.sign("shared-secret", transport.body), transport.signature);
    }

    @Test
    void failureReasonIsSanitizedBeforeSigning() throws Exception {
        OutboundWebhooks hooks = new OutboundWebhooks(temp.resolve("data"));
        OpsWebhookListener listener =
                new OpsWebhookListener(hooks, "https://hooks.example.test/ops", "shared-secret");
        listener.backupFailed("token=abc.def.ghi");

        CapturingTransport transport = new CapturingTransport();
        hooks.deliverAll(transport, 3, Duration.ZERO);
        assertFalse(transport.body.contains("abc.def.ghi"));
        assertTrue(transport.body.contains("[REDACTED]"));
        assertEquals(OutboundWebhooks.sign("shared-secret", transport.body), transport.signature);
    }

    @Test
    void emptyTargetDisablesEmission() throws Exception {
        OutboundWebhooks hooks = new OutboundWebhooks(temp.resolve("data"));
        OpsWebhookListener listener = new OpsWebhookListener(hooks, "", "shared-secret");
        assertFalse(listener.enabled());
        listener.backupSucceeded(Paths.get("backup-1.zip"));
        listener.backupFailed("disk full");
        listener.approvalDecided(new ApprovalQueue.Decision("req-1", "REJECTED", "admin", "no"));
        assertEquals(0, hooks.pendingCount());
    }
}
